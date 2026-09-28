/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

#ifndef REVERIECORECOLORSPACEHOOK_H
#define REVERIECORECOLORSPACEHOOK_H

/**
 * Ensures that KoRgbU8ColorSpace has working difference() and differenceA()
 * implementations using perceptual color distance and alpha weighting.
 *
 * This fixes the issue where Krita's unmanaged KoSimpleColorSpace returns 0
 * for differenceA, causing flood fill and contiguous selection to match
 * every pixel on the canvas when tolerance > 1.
 */
void ensureRgbU8DifferenceHook();

#endif // REVERIECORECOLORSPACEHOOK_H
