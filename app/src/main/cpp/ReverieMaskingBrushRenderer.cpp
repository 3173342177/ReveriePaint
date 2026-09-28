/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * ReverieMaskingBrushRenderer.cpp - Android 定制版 KisMaskingBrushRenderer。
 *
 * 本文件是 Krita libs/ui/tool/strokes/KisMaskingBrushRenderer.cpp 的本地副本,
 * 唯一区别是为掩膜设备的色彩空间增加了空指针兜底 (见下方注释)。
 *
 * 背景 (水彩类预设落笔必崩的根因):
 *   上游实现无条件使用
 *       KoColorSpaceRegistry::instance()->colorSpace(GrayAColorModelID.id(),
 *                                                    Integer8BitsColorDepthID.id())
 *   该灰度色彩空间由 lcms2 色彩引擎插件提供, 并依赖随包发布的
 *   Gray-D50-elle-V2-srgbtrc.icc。Android 构建既不加载该插件也不内置该 ICC,
 *   KoColorSpaceRegistry 里只有 Alpha8 / AlphaU16 / AlphaF32 / Lab / RgbU8 /
 *   RgbU16 六个内置空间, 因此查询返回 nullptr;
 *   new KisPaintDevice(nullptr) 随后在 KoColorSpaceRegistry::permanentColorspace
 *   处解引用空指针, 进程 SIGSEGV 闪退。
 *
 * 兜底策略:
 *   GrayA 不可用时改用内置的 alpha8() (KisAlphaColorSpace)。蒙版合成算子读取的
 *   是掩膜像素的第一个字节 —— GrayA 下为 gray, alpha8 下为 alpha;而掩膜笔刷以
 *   白色 (alpha=255) 涂布, 两者语义完全等价 (gray*alpha == alpha)。
 *   同时改用 KisMaskingBrushCompositeOpFactory::createForAlphaSrc(), 该分支以
 *   mask_is_alpha=true 实例化算子, 正是为 alpha 掩膜准备的官方路径。
 */

#include "KisMaskingBrushRenderer.h"

#include <KoColorSpace.h>
#include <KoColorSpaceRegistry.h>
#include <KoColorModelStandardIds.h>
#include <KoChannelInfo.h>
#include <KoCompositeOpRegistry.h>

#include <algorithm>

#include <android/log.h>
#include <kis_assert.h>

#include "kis_painter.h"
#include "kis_paint_device.h"
#include "kis_random_accessor_ng.h"

#include "KisMaskingBrushCompositeOpBase.h"
#include "KisMaskingBrushCompositeOpFactory.h"

#define MBR_LOG_TAG "RP_MASKING"
#define MBR_LOGW(...) __android_log_print(ANDROID_LOG_WARN, MBR_LOG_TAG, __VA_ARGS__)

namespace {

/**
 * 掩膜设备色彩空间: 首选 Krita 原生的 GrayA/U8, 缺失时回退到内置 alpha8。
 * 返回值保证非空 (alpha8 是 pigment 的内建空间, 始终可用)。
 * @param maskIsAlpha 出参: true 表示返回的色彩空间是纯 alpha 语义。
 */
const KoColorSpace *maskingBrushMaskColorSpace(bool *maskIsAlpha)
{
    KoColorSpaceRegistry *registry = KoColorSpaceRegistry::instance();
    const KoColorSpace *cs = registry->colorSpace(GrayAColorModelID.id(),
                                                  Integer8BitsColorDepthID.id());
    if (cs) {
        if (maskIsAlpha) *maskIsAlpha = false;
        return cs;
    }

    // Android: 无 lcms2 插件 / 无 Gray ICC → 回退 alpha8 (语义等价, 见文件头)。
    cs = registry->alpha8();
    if (cs) {
        if (maskIsAlpha) *maskIsAlpha = true;
        return cs;
    }

    // 理论上不可达: alpha8 是 pigment 内建空间。真到这里时再退到 RGBA8,
    // 保证 KisPaintDevice 永远拿不到 nullptr。
    cs = registry->rgb8();
    if (maskIsAlpha) *maskIsAlpha = false;
    return cs;
}

} // namespace

KisMaskingBrushRenderer::KisMaskingBrushRenderer(KisPaintDeviceSP dstDevice, const QString &compositeOpId)
    : m_dstDevice(dstDevice)
{
    m_strokeDevice = new KisPaintDevice(dstDevice->colorSpace());

    bool maskIsAlpha = false;
    const KoColorSpace *maskColorSpace = maskingBrushMaskColorSpace(&maskIsAlpha);
    m_maskDevice = new KisPaintDevice(maskColorSpace);

    m_strokeDevice->setDefaultBounds(dstDevice->defaultBounds());
    m_maskDevice->setDefaultBounds(dstDevice->defaultBounds());

    const KoColorSpace *dstCs = m_dstDevice->colorSpace();
    const int pixelSize = dstCs->pixelSize();

    KoChannelInfo::enumChannelValueType alphaChannelType = KoChannelInfo::UINT8;
    int alphaChannelOffset = -1;
    int alphaPos = dstCs->alphaPos();

    KIS_SAFE_ASSERT_RECOVER (alphaPos >= 0) {
        alphaPos = 0;
    }

    const QList<KoChannelInfo *> channels = dstCs->channels();
    alphaChannelOffset = channels[alphaPos]->pos()/* * channels[i]->size()*/;
    alphaChannelType = channels[alphaPos]->channelValueType();

    KIS_SAFE_ASSERT_RECOVER (alphaChannelOffset >= 0) {
        alphaChannelOffset = 0;
    }

    // alpha 掩膜必须走 createForAlphaSrc(): 该分支以 mask_is_alpha=true 实例化
    // 合成算子, 直接读取掩膜像素的首字节 (即 alpha) 作为蒙版强度。
    m_compositeOp.reset(
        maskIsAlpha
            ? KisMaskingBrushCompositeOpFactory::createForAlphaSrc(
                  compositeOpId, alphaChannelType, pixelSize, alphaChannelOffset)
            : KisMaskingBrushCompositeOpFactory::create(
                  compositeOpId, alphaChannelType, pixelSize, alphaChannelOffset));

    if (!m_compositeOp) {
        MBR_LOGW("masking brush: composite op '%s' unavailable, falling back to MULT",
                 compositeOpId.toUtf8().constData());
        m_compositeOp.reset(
            maskIsAlpha
                ? KisMaskingBrushCompositeOpFactory::createForAlphaSrc(
                      COMPOSITE_MULT, alphaChannelType, pixelSize, alphaChannelOffset)
                : KisMaskingBrushCompositeOpFactory::create(
                      COMPOSITE_MULT, alphaChannelType, pixelSize, alphaChannelOffset));
    }
}

KisMaskingBrushRenderer::~KisMaskingBrushRenderer()
{
}

KisPaintDeviceSP KisMaskingBrushRenderer::strokeDevice() const
{
    return m_strokeDevice;
}

KisPaintDeviceSP KisMaskingBrushRenderer::maskDevice() const
{
    return m_maskDevice;
}

void KisMaskingBrushRenderer::updateProjection(const QRect &rc)
{
    if (rc.isEmpty()) return;

    KisPainter::copyAreaOptimized(rc.topLeft(), m_strokeDevice, m_dstDevice, rc);

    KisRandomAccessorSP dstIt = m_dstDevice->createRandomAccessorNG();
    KisRandomConstAccessorSP maskIt = m_maskDevice->createRandomConstAccessorNG();

    qint32 dstY = rc.y();
    qint32 rowsRemaining = rc.height();

    while (rowsRemaining > 0) {
        qint32 dstX = rc.x();

        const qint32 numContiguousDstRows = dstIt->numContiguousRows(dstY);
        const qint32 numContiguousMaskRows = maskIt->numContiguousRows(dstY);

        const qint32 rows = std::min({rowsRemaining, numContiguousDstRows, numContiguousMaskRows});

        qint32 columnsRemaining = rc.width();

        while (columnsRemaining > 0) {

            const qint32 numContiguousDstColumns = dstIt->numContiguousColumns(dstX);
            const qint32 numContiguousMaskColumns = maskIt->numContiguousColumns(dstX);
            const qint32 columns = std::min({columnsRemaining, numContiguousDstColumns, numContiguousMaskColumns});

            const qint32 dstRowStride = dstIt->rowStride(dstX, dstY);
            const qint32 maskRowStride = maskIt->rowStride(dstX, dstY);

            dstIt->moveTo(dstX, dstY);
            maskIt->moveTo(dstX, dstY);

            m_compositeOp->composite(maskIt->rawDataConst(), maskRowStride,
                                     dstIt->rawData(), dstRowStride,
                                     columns, rows);

            dstX += columns;
            columnsRemaining -= columns;
        }

        dstY += rows;
        rowsRemaining -= rows;
    }
}
