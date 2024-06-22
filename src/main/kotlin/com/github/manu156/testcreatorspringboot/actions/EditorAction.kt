package com.github.manu156.testcreatorspringboot.actions

import com.github.manu156.testcreatorspringboot.dto.Node
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.stream

class EditorAction: AnAction() {
    val log = com.intellij.openapi.diagnostic.Logger.getInstance(EditorAction::class.java)
    override fun actionPerformed(e: AnActionEvent) {
        val psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE)
        if (!"JAVA".equals(psiFile.fileType.name, ignoreCase = true)) {
            return
        }
        val caret = e.getRequiredData(CommonDataKeys.CARET)
        val psiElementAtCaretOffset = psiFile.findElementAt(caret.offset)
        val psiTargetMethod = PsiTreeUtil.getParentOfType(psiElementAtCaretOffset, PsiMethod::class.java)
        // todo: validate if test cases can be generated for this method that is it is not private or anything like this

        val autoWiredDeps = getAllAutoWiredDeps(psiFile)
        if (psiTargetMethod == null || psiElementAtCaretOffset == null) {
            log.warn("psiElementAtCaretOffset or psiTargetMethod is null")
            return
        }
        val testCases = generateTestsForMethod(psiElementAtCaretOffset, psiTargetMethod, autoWiredDeps)
        log.info("tests: $testCases")
    }

    private fun generateTestsForMethod(
        psiElementAtCaret: PsiElement,
        psiTargetMethod: PsiMethod,
        autoWiredDeps: Map<String, String>
    ): List<String> {
        val root: Node = Node()
        root.children = ArrayList<Node>()
        root.body = psiTargetMethod.body
        val stack = ArrayDeque<Node>()

        if (psiTargetMethod.body == null) {
            return emptyList()
        }

        for (psiElement in psiTargetMethod.body!!.children) {
            if (psiElement is PsiIfStatement) {
                val node = Node()
                node.psiElement = psiElement
                node.body = psiElement
                root.children!!.add(node)
                stack.add(node)
            }
        }

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current.discovered) {
                continue
            }
            if (current.children == null) {
                current.children = ArrayList<Node>()
            }

            var currentIfPsi = current.psiElement;
            while (currentIfPsi is PsiIfStatement) {
                // last if
                if (currentIfPsi.elseBranch == null) {
                    // todo: test
                    val node = Node()
                    node.psiElement = currentIfPsi.thenBranch
                    current.children!!.add(node)
                    stack.add(node)
                    break
                }
                // last if else
                if (currentIfPsi.elseBranch is PsiBlockStatement) {
                    val node = Node()
                    node.psiElement = currentIfPsi.thenBranch
                    current.children!!.add(node)
                    stack.add(node)

                    val node2 = Node()
                    node2.psiElement = currentIfPsi.elseBranch
                    current.children!!.add(node2)
                    stack.add(node2)
                    break
                }
                if (currentIfPsi.thenBranch != null) {
                    val node = Node()
                    node.psiElement = currentIfPsi.thenBranch
                    current.children!!.add(node)
                    stack.add(node)
                    currentIfPsi = currentIfPsi.elseBranch
                }
            }
            current.discovered = true
        }
        root.discovered = true


        val dfsStack = ArrayDeque<Node>()
        dfsStack.add(root)
        while (!dfsStack.isEmpty()) {
            val current = dfsStack.last()
            if (current.expIndex != null && current.children != null && current.expIndex!! >= current.children!!.size) {
                dfsStack.removeLast()
                continue
            }
            if (current.expIndex == null) {
                if (current.children != null && current.children!!.isNotEmpty()) {
                    if (current.children!!.size > 1 || current.children!!.get(0).children?.size!! > 1) {
                        // todo: fix by separating body and children
                        dfsStack.add(current.children!!.first())
                        current.expIndex = 0
                        continue
                    }
                }
            }
            if (current.children == null || current.children!!.isEmpty()) {
                // go 1 step back
                dfsStack.removeLast()
//                current.expIndex = current.expIndex!! + 1
            }
            if (current.expIndex != null && current.children != null && current.children!!.size > 0) {

            //                current.expIndex = current.expIndex!! + 1
//                continue
            }

        }

        return emptyList()
    }

    private fun getAllAutoWiredDeps(psiFile: PsiFile?): Map<String, String> {
        val fieldNameToClass = mutableMapOf<String, String>()
        if (psiFile == null) {
            return fieldNameToClass
        }
        val classDeclarations = PsiTreeUtil.getChildrenOfType(psiFile, PsiClass::class.java)
        if (classDeclarations != null) {
            for (classDeclaration in classDeclarations) {
                val fieldDeclarations = PsiTreeUtil.getChildrenOfType(classDeclaration, PsiField::class.java)
                if (fieldDeclarations.isNullOrEmpty()) {
                    continue
                }
                for (fieldDeclaration in fieldDeclarations) {
                    if (fieldDeclaration.annotations.stream().anyMatch{t -> t.resolveAnnotationType()?.name == "Autowired" }) {
                        fieldNameToClass[fieldDeclaration.name] = (fieldDeclaration.type as PsiClassReferenceType).className
                    }
                }
            }
        }

        return fieldNameToClass
    }
}