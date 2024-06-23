package com.github.manu156.testcreatorspringboot.actions

import com.github.manu156.testcreatorspringboot.dto.Node
import com.github.manu156.testcreatorspringboot.dto.NodeType
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
        val root = Node()
        root.psiElement = psiTargetMethod.body
        root.cIds = ArrayList()
        root.cPsis = ArrayList()
        root.nodeType = NodeType.Body
        root.children = ArrayList()
        val stack = ArrayDeque<Node>()

        if (psiTargetMethod.body == null) {
            return emptyList()
        }

        @OptIn(kotlin.ExperimentalStdlibApi::class)
        for (i in 0 ..< psiTargetMethod.body!!.children.size) {
            val psiElement = psiTargetMethod.body!!.children[i]
            if (psiElement is PsiIfStatement) {
                val node = Node()
                node.nodeType = NodeType.Conditional
                node.psiElement = psiElement
                root.cIds!!.add(i)
                root.cPsis = ArrayList()
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
                current.children = ArrayList()
            }

            if (current.nodeType == NodeType.Body) {
                if (current.cIds == null) {
                    current.cIds = ArrayList()
                }
                @OptIn(kotlin.ExperimentalStdlibApi::class)
                for (i in 0 ..< current.psiElement!!.children.size) {
                    val psiElement = current.psiElement!!.children[i]
                    if (psiElement is PsiIfStatement) {
                        val node = Node()
                        node.nodeType = NodeType.Conditional
                        node.psiElement = psiElement
                        root.cIds!!.add(i)
                        root.cPsis = ArrayList()
                        root.children!!.add(node)
                        stack.add(node)
                    }
                }
            } else if (current.nodeType == NodeType.Conditional) {
                if (current.cPsis == null) {
                    current.cPsis = ArrayList()
                }
                var currentIfPsi = current.psiElement;
                while (currentIfPsi is PsiIfStatement) {
                    // last if
                    if (currentIfPsi.elseBranch == null) {
                        // todo: test
                        val node = Node()
                        node.nodeType = NodeType.Body
                        node.psiElement = currentIfPsi.thenBranch
                        current.cPsis!!.add(currentIfPsi)
                        current.children!!.add(node)
                        stack.add(node)
                        break
                    }
                    // last if else
                    if (currentIfPsi.elseBranch is PsiBlockStatement) {
                        val node = Node()
                        node.nodeType = NodeType.Body
                        node.psiElement = currentIfPsi.thenBranch
                        current.cPsis!!.add(currentIfPsi)
                        current.children!!.add(node)
                        stack.add(node)

                        val node2 = Node()
                        node2.nodeType = NodeType.Body
                        node2.psiElement = currentIfPsi.elseBranch
                        current.cPsis!!.add(currentIfPsi)
                        current.children!!.add(node)
                        stack.add(node2)
                        break
                    }
                    if (currentIfPsi.thenBranch != null) {
                        val node = Node()
                        node.nodeType = NodeType.Body
                        node.psiElement = currentIfPsi.thenBranch
                        current.cPsis!!.add(currentIfPsi)
                        current.children!!.add(node)
                        stack.add(node)
                        currentIfPsi = currentIfPsi.elseBranch
                    }
                }
            }
            current.discovered = true
        }
        root.discovered = true

        /*
        pathHashes = {}
        tests = []
        stack = []
        stack.add(root)
        while(stack.isNotEmpty()) {
            path = calculatePath() {
                node = stack.last()
                // expIndex states:
                //  null -> never used
                //  k, k>=0 -> kth left branch
                // state transfer:
                //  if null -> set 0 and goto next node
                //  if not last node (>=0) -> goto next node
                //  if last node and less than children increment
                //  else
                //  -> if left node exists -> goto left node and increment and mark all the children null
                //  if max on all left nodes then increment grandparent node and mark all children null
            }
            if (path == null) {
                break
            }
            hash = calculatePathHash(path) // check feasibility as well
            if hash not in pathHashes {
                tests.add(calculateTest(path))
            }
        }
         */

        val paths = mutableListOf<MutableList<Node>>()
        val currentPath = mutableListOf<String>()
        val pathHashes = mutableSetOf<String>()
        val testStrings = mutableSetOf<String>()
        val frameStack = ArrayDeque<ArrayDeque<Node>>()
        frameStack.add(ArrayDeque())
        frameStack.first().add(root)
        while (frameStack.isNotEmpty()) {
            val nodeStack = frameStack.last()
            if (nodeStack.isEmpty()) {
                frameStack.removeLast()
                continue
            }
            val isBodyTypeNode = nodeStack.first().nodeType == NodeType.Body
            if (isBodyTypeNode) {
                for (node in nodeStack) {
                    if (node.expIndex == null) {
                        node.expIndex = 0

                    }
                }
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