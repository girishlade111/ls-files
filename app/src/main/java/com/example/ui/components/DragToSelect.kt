package com.example.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.data.model.FileItem
import com.example.ui.util.rememberAppHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun Modifier.dragSelectLazyList(
    listState: LazyListState,
    items: List<FileItem>,
    selectedPaths: Set<String>,
    onSelectionChange: (Set<String>) -> Unit
): Modifier {
    val haptic = rememberAppHapticFeedback()
    val density = LocalDensity.current

    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var currentDragIndex by remember { mutableStateOf<Int?>(null) }
    var initialSelectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragPointerOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    val autoScrollThresholdPx = with(density) { 48.dp.toPx() }

    // Edge auto-scroll loop
    LaunchedEffect(isDragging, dragPointerOffset) {
        if (!isDragging) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height.toFloat()

        if (viewportHeight <= 0f) return@LaunchedEffect

        val distanceFromTop = dragPointerOffset.y
        val distanceFromBottom = viewportHeight - dragPointerOffset.y

        val scrollDelta = when {
            distanceFromTop in 0f..autoScrollThresholdPx -> {
                -((autoScrollThresholdPx - distanceFromTop) / autoScrollThresholdPx) * 25f
            }
            distanceFromBottom in 0f..autoScrollThresholdPx -> {
                ((autoScrollThresholdPx - distanceFromBottom) / autoScrollThresholdPx) * 25f
            }
            else -> 0f
        }

        if (scrollDelta != 0f) {
            while (isActive && isDragging) {
                listState.scrollBy(scrollDelta)
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val currentItem = visibleItems.firstOrNull { item ->
                    dragPointerOffset.y.toInt() in item.offset..(item.offset + item.size)
                }
                if (currentItem != null && currentItem.index in items.indices) {
                    currentDragIndex = currentItem.index
                    val start = dragStartIndex
                    val current = currentDragIndex
                    if (start != null && current != null) {
                        val min = minOf(start, current)
                        val max = maxOf(start, current)
                        val rangePaths = items.subList(min, max + 1).map { it.path }.toSet()
                        onSelectionChange(initialSelectedPaths + rangePaths)
                    }
                }
                delay(16)
            }
        }
    }

    return this.pointerInput(items, listState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                dragPointerOffset = offset
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val hitItem = visibleItems.firstOrNull { item ->
                    offset.y.toInt() in item.offset..(item.offset + item.size)
                }
                if (hitItem != null && hitItem.index in items.indices) {
                    haptic.performLongPress()
                    isDragging = true
                    dragStartIndex = hitItem.index
                    currentDragIndex = hitItem.index
                    initialSelectedPaths = selectedPaths
                    val startFile = items[hitItem.index]
                    onSelectionChange(initialSelectedPaths + startFile.path)
                }
            },
            onDrag = { change, _ ->
                change.consume()
                dragPointerOffset = change.position
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val hitItem = visibleItems.firstOrNull { item ->
                    change.position.y.toInt() in item.offset..(item.offset + item.size)
                }
                if (hitItem != null && hitItem.index in items.indices) {
                    currentDragIndex = hitItem.index
                    val start = dragStartIndex
                    val current = currentDragIndex
                    if (start != null && current != null) {
                        val min = minOf(start, current)
                        val max = maxOf(start, current)
                        val rangePaths = items.subList(min, max + 1).map { it.path }.toSet()
                        onSelectionChange(initialSelectedPaths + rangePaths)
                    }
                }
            },
            onDragEnd = {
                isDragging = false
                dragStartIndex = null
                currentDragIndex = null
            },
            onDragCancel = {
                isDragging = false
                dragStartIndex = null
                currentDragIndex = null
            }
        )
    }
}

@Composable
fun Modifier.dragSelectLazyRow(
    listState: LazyListState,
    items: List<FileItem>,
    selectedPaths: Set<String>,
    onSelectionChange: (Set<String>) -> Unit
): Modifier {
    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var currentDragIndex by remember { mutableStateOf<Int?>(null) }
    var initialSelectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragPointerOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    return this.pointerInput(items, listState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                dragPointerOffset = offset
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val hitItem = visibleItems.firstOrNull { item ->
                    offset.x.toInt() in item.offset..(item.offset + item.size)
                }
                if (hitItem != null && hitItem.index in items.indices) {
                    isDragging = true
                    dragStartIndex = hitItem.index
                    currentDragIndex = hitItem.index
                    initialSelectedPaths = selectedPaths
                    val startFile = items[hitItem.index]
                    onSelectionChange(initialSelectedPaths + startFile.path)
                }
            },
            onDrag = { change, _ ->
                change.consume()
                dragPointerOffset = change.position
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val hitItem = visibleItems.firstOrNull { item ->
                    change.position.x.toInt() in item.offset..(item.offset + item.size)
                }
                if (hitItem != null && hitItem.index in items.indices) {
                    currentDragIndex = hitItem.index
                    val start = dragStartIndex
                    val current = currentDragIndex
                    if (start != null && current != null) {
                        val min = minOf(start, current)
                        val max = maxOf(start, current)
                        val rangePaths = items.subList(min, max + 1).map { it.path }.toSet()
                        onSelectionChange(initialSelectedPaths + rangePaths)
                    }
                }
            },
            onDragEnd = {
                isDragging = false
                dragStartIndex = null
                currentDragIndex = null
            },
            onDragCancel = {
                isDragging = false
                dragStartIndex = null
                currentDragIndex = null
            }
        )
    }
}

@Composable
fun Modifier.dragSelectLazyGrid(
    gridState: LazyGridState,
    items: List<FileItem>,
    selectedPaths: Set<String>,
    onSelectionChange: (Set<String>) -> Unit
): Modifier {
    val haptic = rememberAppHapticFeedback()
    val density = LocalDensity.current

    var dragStartIndex by remember { mutableStateOf<Int?>(null) }
    var currentDragIndex by remember { mutableStateOf<Int?>(null) }
    var initialSelectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var dragPointerOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    val autoScrollThresholdPx = with(density) { 48.dp.toPx() }

    LaunchedEffect(isDragging, dragPointerOffset) {
        if (!isDragging) return@LaunchedEffect
        val layoutInfo = gridState.layoutInfo
        val viewportHeight = layoutInfo.viewportSize.height.toFloat()

        if (viewportHeight <= 0f) return@LaunchedEffect

        val distanceFromTop = dragPointerOffset.y
        val distanceFromBottom = viewportHeight - dragPointerOffset.y

        val scrollDelta = when {
            distanceFromTop in 0f..autoScrollThresholdPx -> {
                -((autoScrollThresholdPx - distanceFromTop) / autoScrollThresholdPx) * 25f
            }
            distanceFromBottom in 0f..autoScrollThresholdPx -> {
                ((autoScrollThresholdPx - distanceFromBottom) / autoScrollThresholdPx) * 25f
            }
            else -> 0f
        }

        if (scrollDelta != 0f) {
            while (isActive && isDragging) {
                gridState.scrollBy(scrollDelta)
                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                val hitItem = visibleItems.firstOrNull { item ->
                    val x = dragPointerOffset.x.toInt()
                    val y = dragPointerOffset.y.toInt()
                    x in item.offset.x..(item.offset.x + item.size.width) &&
                    y in item.offset.y..(item.offset.y + item.size.height)
                }
                if (hitItem != null && hitItem.index in items.indices) {
                    currentDragIndex = hitItem.index
                    val start = dragStartIndex
                    val current = currentDragIndex
                    if (start != null && current != null) {
                        val min = minOf(start, current)
                        val max = maxOf(start, current)
                        val rangePaths = items.subList(min, max + 1).map { it.path }.toSet()
                        onSelectionChange(initialSelectedPaths + rangePaths)
                    }
                }
                delay(16)
            }
        }
    }

    return this.pointerInput(items, gridState) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                dragPointerOffset = offset
                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                val hitItem = visibleItems.firstOrNull { item ->
                    val x = offset.x.toInt()
                    val y = offset.y.toInt()
                    x in item.offset.x..(item.offset.x + item.size.width) &&
                    y in item.offset.y..(item.offset.y + item.size.height)
                }
                if (hitItem != null && hitItem.index in items.indices) {
                    haptic.performLongPress()
                    isDragging = true
                    dragStartIndex = hitItem.index
                    currentDragIndex = hitItem.index
                    initialSelectedPaths = selectedPaths
                    val startFile = items[hitItem.index]
                    onSelectionChange(initialSelectedPaths + startFile.path)
                }
            },
            onDrag = { change, _ ->
                change.consume()
                dragPointerOffset = change.position
                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                val hitItem = visibleItems.firstOrNull { item ->
                    val x = change.position.x.toInt()
                    val y = change.position.y.toInt()
                    x in item.offset.x..(item.offset.x + item.size.width) &&
                    y in item.offset.y..(item.offset.y + item.size.height)
                }
                if (hitItem != null && hitItem.index in items.indices) {
                    currentDragIndex = hitItem.index
                    val start = dragStartIndex
                    val current = currentDragIndex
                    if (start != null && current != null) {
                        val min = minOf(start, current)
                        val max = maxOf(start, current)
                        val rangePaths = items.subList(min, max + 1).map { it.path }.toSet()
                        onSelectionChange(initialSelectedPaths + rangePaths)
                    }
                }
            },
            onDragEnd = {
                isDragging = false
                dragStartIndex = null
                currentDragIndex = null
            },
            onDragCancel = {
                isDragging = false
                dragStartIndex = null
                currentDragIndex = null
            }
        )
    }
}
