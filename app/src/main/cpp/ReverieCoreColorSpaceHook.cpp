/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#include "ReverieCoreColorSpaceHook.h"

#include <KoColorSpace.h>
#include <KoColorSpaceRegistry.h>

#include <android/log.h>
#include <sys/mman.h>
#include <unistd.h>
#include <cmath>
#include <algorithm>
#include <cstring>

#define LOG_TAG "RP_COLOR"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

/**
 * Custom difference function for KoRgbU8ColorSpace (BGR order).
 * Computes perceptual color difference (ignoring alpha) normalized to 0..100.
 */
quint8 customDifference(const KoColorSpace * /*cs*/, const quint8 *src1, const quint8 *src2)
{
    if (!src1 || !src2) return 0;

    // KoBgrU8Traits: [0]=B, [1]=G, [2]=R, [3]=A
    const double db = static_cast<double>(src1[0]) - static_cast<double>(src2[0]);
    const double dg = static_cast<double>(src1[1]) - static_cast<double>(src2[1]);
    const double dr = static_cast<double>(src1[2]) - static_cast<double>(src2[2]);

    // ITU-R BT.601 perceptual luminance weights: R: 0.299, G: 0.587, B: 0.114
    const double colorDist = std::sqrt(0.299 * dr * dr + 0.587 * dg * dg + 0.114 * db * db);
    const double normColor = (colorDist / 255.0) * 100.0;

    return static_cast<quint8>(std::min(100.0, std::round(normColor)));
}

/**
 * Custom differenceA function for KoRgbU8ColorSpace (BGR order).
 * Computes perceptual color + alpha difference normalized to 0..100.
 *
 * Fully transparent pixels stop at opaque lineart (difference = 100),
 * allowing flood fill and magic wand to properly detect boundary lines.
 */
quint8 customDifferenceA(const KoColorSpace * /*cs*/, const quint8 *src1, const quint8 *src2)
{
    if (!src1 || !src2) return 0;

    const int b1 = src1[0], g1 = src1[1], r1 = src1[2], a1 = src1[3];
    const int b2 = src2[0], g2 = src2[1], r2 = src2[2], a2 = src2[3];

    // Case 1: Both fully transparent -> 0 difference (both in empty region)
    if (a1 == 0 && a2 == 0) {
        return 0;
    }

    // Case 2: One is fully transparent -> purely alpha difference normalized to 0..100
    // (aligns with Krita LcmsColorSpace: alphaScale = 100.0 / 255.0)
    if (a1 == 0 || a2 == 0) {
        const double diffA = (std::abs(a1 - a2) / 255.0) * 100.0;
        return static_cast<quint8>(std::min(100.0, std::round(diffA)));
    }

    // Case 3: Both have color / opacity
    const double db = b1 - b2;
    const double dg = g1 - g2;
    const double dr = r1 - r2;
    const double da = a1 - a2;

    const double colorDist = std::sqrt(0.299 * dr * dr + 0.587 * dg * dg + 0.114 * db * db);
    const double normColor = (colorDist / 255.0) * 100.0;
    const double normAlpha = (std::abs(da) / 255.0) * 100.0;

    const double totalDiff = std::sqrt(normColor * normColor + normAlpha * normAlpha);
    return static_cast<quint8>(std::min(100.0, std::round(totalDiff)));
}

// Backup custom vtable in case mprotect on .data.rel.ro fails
void *s_hookedVtable[256];
bool s_hookInstalled = false;

} // namespace

void ensureRgbU8DifferenceHook()
{
    if (s_hookInstalled) return;

    const KoColorSpace *cs = KoColorSpaceRegistry::instance()->rgb8();
    if (!cs) {
        LOGE("ensureRgbU8DifferenceHook: rgb8() returned null");
        return;
    }

    void **origVtable = *reinterpret_cast<void***>(const_cast<KoColorSpace*>(cs));
    if (!origVtable) {
        LOGE("ensureRgbU8DifferenceHook: null vtable on rgb8() instance");
        return;
    }

    // Itanium C++ ABI on ARM64: member function pointer contains byte offset in vtable
    union Pmf {
        quint8 (KoColorSpace::*fn)(const quint8*, const quint8*) const;
        struct {
            uintptr_t ptr;
            ptrdiff_t adj;
        } s;
    };

    Pmf pmfDiff;
    pmfDiff.fn = &KoColorSpace::difference;

    Pmf pmfDiffA;
    pmfDiffA.fn = &KoColorSpace::differenceA;

    size_t slotDiff = 0;
    size_t slotDiffA = 0;

    if (pmfDiff.s.adj & 1) {
        slotDiff = pmfDiff.s.ptr / sizeof(void*);
    } else {
        // Fallback to verified slot from libkritapigment.so
        slotDiff = 58;
    }

    if (pmfDiffA.s.adj & 1) {
        slotDiffA = pmfDiffA.s.ptr / sizeof(void*);
    } else {
        // Fallback to verified slot from libkritapigment.so
        slotDiffA = 59;
    }

    LOGI("ensureRgbU8DifferenceHook: origVtable=%p, slotDiff=%zu, slotDiffA=%zu",
         origVtable, slotDiff, slotDiffA);

    bool patchedDirectly = false;
    const size_t pageSize = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    uintptr_t addr = reinterpret_cast<uintptr_t>(&origVtable[slotDiff]);
    uintptr_t pageStart = addr & ~(pageSize - 1);
    size_t len = (reinterpret_cast<uintptr_t>(&origVtable[slotDiffA + 1]) - pageStart + pageSize - 1) & ~(pageSize - 1);

    if (mprotect(reinterpret_cast<void*>(pageStart), len, PROT_READ | PROT_WRITE) == 0) {
        origVtable[slotDiff] = reinterpret_cast<void*>(&customDifference);
        origVtable[slotDiffA] = reinterpret_cast<void*>(&customDifferenceA);
        mprotect(reinterpret_cast<void*>(pageStart), len, PROT_READ);
        patchedDirectly = true;
        LOGI("ensureRgbU8DifferenceHook: directly patched shared vtable in memory");
    } else {
        LOGW("ensureRgbU8DifferenceHook: mprotect failed, falling back to instance vtable replacement");
        std::memcpy(s_hookedVtable, origVtable, 128 * sizeof(void*));
        s_hookedVtable[slotDiff] = reinterpret_cast<void*>(&customDifference);
        s_hookedVtable[slotDiffA] = reinterpret_cast<void*>(&customDifferenceA);
        *reinterpret_cast<void***>(const_cast<KoColorSpace*>(cs)) = s_hookedVtable;
    }

    s_hookInstalled = true;

    // Self-verification test
    const quint8 transPixel[4] = {0, 0, 0, 0};
    const quint8 blackOpaque[4] = {0, 0, 0, 255};
    const quint8 redOpaque[4] = {0, 0, 255, 255};
    const quint8 blueOpaque[4] = {255, 0, 0, 255};

    const quint8 diffTransToBlack = cs->differenceA(transPixel, blackOpaque);
    const quint8 diffSame = cs->differenceA(blackOpaque, blackOpaque);
    const quint8 diffRedBlue = cs->differenceA(redOpaque, blueOpaque);

    LOGI("ensureRgbU8DifferenceHook verification: trans->black=%d (expected 100), same=%d (expected 0), red->blue=%d (expected ~65)",
         diffTransToBlack, diffSame, diffRedBlue);
}
