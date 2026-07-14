package com.pdfapp.ui

/**
 * Top-level mode of an open document: [READ] is the continuous-scroll viewer
 * (search, selection, outline); [EDIT] is the single-page overlay editor
 * (sign, text, move).
 */
enum class ViewerMode { READ, EDIT }

/** Reader zoom presets offered in the menu (plan 2.6). */
enum class ZoomPreset { FIT_WIDTH, FIT_PAGE }
