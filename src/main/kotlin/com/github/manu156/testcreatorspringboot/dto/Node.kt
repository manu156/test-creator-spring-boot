package com.github.manu156.testcreatorspringboot.dto

import com.intellij.psi.PsiElement

class Node {
    var key: Any? = null
    var children: MutableList<Node>? = null
    var discovered: Boolean = false
    var psiElement: PsiElement? = null
    var body: PsiElement? = null
    var expIndex: Int? = null
}