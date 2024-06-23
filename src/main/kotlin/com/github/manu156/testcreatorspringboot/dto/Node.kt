package com.github.manu156.testcreatorspringboot.dto

import com.intellij.psi.PsiElement

class Node {
    var discovered: Boolean = false
    var children: MutableList<Node>? = null
    var parent: Node? = null
    /** psiElement of this node including conditional or any boundaries except for main method */
    var psiElement: PsiElement? = null
    /** exploration index for BFS/DFS/Search */
    var expIndex: Int? = null
    var nodeType: NodeType? = null
    /** Conditional statement ids in this psiElement at same level */
    var cIds: MutableList<Int>? = null
    var cPsis: MutableList<PsiElement>? = null

    fun cloneWithNonRecursiveParameters(): Node {
        val target = Node()
        target.discovered = this.discovered
        target.psiElement = this.psiElement
        target.nodeType = this.nodeType
        target.cIds = this.cIds
        target.cPsis = this.cPsis
        return target
    }
}