package com.ash.simpledataentry.presentation.core

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ash.simpledataentry.domain.model.OrganisationUnit

private data class OrgUnitTreeNode(
    val name: String,
    val path: String,
    var id: String? = null,
    val children: MutableList<OrgUnitTreeNode> = mutableListOf()
)

private data class OrgUnitTreeItem(
    val node: OrgUnitTreeNode,
    val depth: Int,
    val hasChildren: Boolean
)

@Composable
fun OrgUnitTreePickerDialog(
    orgUnits: List<OrganisationUnit>,
    selectedOrgUnitId: String?,
    onOrgUnitSelected: (OrganisationUnit) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Select Organization Unit"
) {
    var query by remember { mutableStateOf("") }
    val tree = remember(orgUnits) { buildOrgUnitTree(orgUnits) }
    val orgUnitMap = remember(orgUnits) { orgUnits.associateBy { it.id } }
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    val filteredTree = remember(tree, query) { filterTree(tree, query) }
    val autoExpand = query.isNotBlank()
    val visibleItems = remember(filteredTree, expandedPaths, autoExpand) {
        flattenTree(filteredTree, expandedPaths, autoExpand)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search organization units...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    }
                )

                if (visibleItems.isEmpty()) {
                    Text(
                        text = "No organization units found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn {
                        items(visibleItems, key = { it.node.path }) { item ->
                            OrgUnitTreeRow(
                                item = item,
                                isExpanded = expandedPaths.contains(item.node.path),
                                isSelected = item.node.id != null && item.node.id == selectedOrgUnitId,
                                onToggle = {
                                    expandedPaths = if (expandedPaths.contains(item.node.path)) {
                                        expandedPaths - item.node.path
                                    } else {
                                        expandedPaths + item.node.path
                                    }
                                },
                                onSelect = {
                                    item.node.id?.let { id ->
                                        orgUnitMap[id]?.let { onOrgUnitSelected(it) }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun OrgUnitTreeRow(
    item: OrgUnitTreeItem,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit
) {
    val indent = (item.depth * 16).dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent, top = 4.dp, bottom = 4.dp)
            .clickable(enabled = item.node.id != null) { onSelect() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item.hasChildren) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }

        Text(
            text = item.node.name,
            style = if (isSelected) {
                MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun buildOrgUnitTree(orgUnits: List<OrganisationUnit>): List<OrgUnitTreeNode> {
    val root = OrgUnitTreeNode("root", "root")
    orgUnits.forEach { orgUnit ->
        val pathSource = orgUnit.path ?: orgUnit.name
        val segments = pathSource.split("/")
            .filter { it.isNotBlank() }
        var parent = root
        var currentPath = ""
        segments.forEachIndexed { index, segment ->
            currentPath += "/$segment"
            val existing = parent.children.firstOrNull { it.name == segment }
            val node = existing ?: OrgUnitTreeNode(segment, currentPath).also {
                parent.children.add(it)
            }
            if (index == segments.lastIndex) {
                node.id = orgUnit.id
            }
            parent = node
        }
    }
    sortTree(root)
    return root.children
}

private fun sortTree(node: OrgUnitTreeNode) {
    node.children.sortBy { it.name.lowercase() }
    node.children.forEach { sortTree(it) }
}

private fun filterTree(nodes: List<OrgUnitTreeNode>, query: String): List<OrgUnitTreeNode> {
    if (query.isBlank()) return nodes
    val lowered = query.trim().lowercase()
    return nodes.mapNotNull { node ->
        val filteredChildren = filterTree(node.children, query)
        val matches = node.name.lowercase().contains(lowered) ||
            node.path.lowercase().contains(lowered)
        if (matches || filteredChildren.isNotEmpty()) {
            node.copy(children = filteredChildren.toMutableList())
        } else {
            null
        }
    }
}

private fun flattenTree(
    nodes: List<OrgUnitTreeNode>,
    expandedPaths: Set<String>,
    forceExpand: Boolean,
    depth: Int = 0
): List<OrgUnitTreeItem> {
    val items = mutableListOf<OrgUnitTreeItem>()
    nodes.forEach { node ->
        val hasChildren = node.children.isNotEmpty()
        items.add(OrgUnitTreeItem(node = node, depth = depth, hasChildren = hasChildren))
        val shouldExpand = forceExpand || expandedPaths.contains(node.path)
        if (hasChildren && shouldExpand) {
            items.addAll(flattenTree(node.children, expandedPaths, forceExpand, depth + 1))
        }
    }
    return items
}
