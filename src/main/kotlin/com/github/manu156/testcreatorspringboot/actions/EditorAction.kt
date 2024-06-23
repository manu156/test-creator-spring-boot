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
                node.parent = root
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
                        node.parent = current
                        current.cIds!!.add(i)
                        current.cPsis = ArrayList()
                        current.children!!.add(node)
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
                        node.parent = current
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
                        node.parent = current
                        current.cPsis!!.add(currentIfPsi)
                        current.children!!.add(node)
                        stack.add(node)

                        val node2 = Node()
                        node2.nodeType = NodeType.Body
                        node2.psiElement = currentIfPsi.elseBranch
                        node2.parent = current
                        current.cPsis!!.add(currentIfPsi)
                        current.children!!.add(node)
                        stack.add(node2)
                        break
                    }
                    if (currentIfPsi.thenBranch != null) {
                        val node = Node()
                        node.nodeType = NodeType.Body
                        node.psiElement = currentIfPsi.thenBranch
                        node.parent = current
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

        val traversalStack = ArrayDeque<Node>()
        traversalStack.add(root)
        var constructionStack = ArrayDeque<Node>()
        var constructionRoot = root.cloneWithNonRecursiveParameters()
        constructionStack.add(constructionRoot)
        while (traversalStack.isNotEmpty()) {
            val node = traversalStack.removeLast()
            val currentConstructionNode = constructionStack.removeLast()
            if (node.children.isNullOrEmpty()) {
                if (traversalStack.isEmpty()) {
                    // we have completed generation of one traversal of tree
                    // -> update counters
                    // -> generate test
                    // -> reset constructionStack
                    // add back root with updated counters

                    // todo: break for debugging
                    log.info(constructionRoot.toString())

                    // since we are following stack, first child of root will be current node
                    // let's just try to increment any lowest value
                    // do simple dfs and find such a node
                    // we can always increase a leaf's node's index as long it is less than children.size
                    // we can increase a node's index if and only if all the children have max index

                    // hash current parse tree
                    // save testcase

                    // do dfs
                    val dfsStack = ArrayDeque<Node>()
                    var targetNode: Node?

                    // after finding target node
                    // termination condition. if target node == root and in last index -> break
                    // else: -> traverse until we can increase any node index

                } else {
                    // final state, we have parsed all possibilities
                    // todo: continue not needed here??
                    continue
                }
            } else {
                for (child in node.children!!) {
                    if (child.children.isNullOrEmpty()) {
                        continue
                    }
                    if (currentConstructionNode.children.isNullOrEmpty()) {
                        currentConstructionNode.children = ArrayList()
                    }
                    if (child.expIndex == null) {
                        child.expIndex = 0
                    }
                    if (!child.children.isNullOrEmpty()) {
                        val grandChild = child.children!![child.expIndex!!]
                        traversalStack.add(grandChild)
                        constructionStack.add(grandChild.cloneWithNonRecursiveParameters())
                        currentConstructionNode.children!!.add(grandChild)
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