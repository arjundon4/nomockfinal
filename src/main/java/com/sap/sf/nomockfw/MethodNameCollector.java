package com.sap.sf.nomockfw;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.HashSet;
import java.util.List;

public class MethodNameCollector extends VoidVisitorAdapter<List<MethodDeclaration>> {
    @Override
    public void visit(MethodDeclaration md, List<MethodDeclaration> methodList) {
        super.visit(md, methodList);
        NodeList<Modifier> modifiers = md.getModifiers();
        for (Modifier modifier:modifiers) {
            if(modifier.getKeyword().equals(Modifier.Keyword.PUBLIC) || modifier.getKeyword().equals(Modifier.Keyword.PROTECTED)) {
                methodList.add(md);
            }
        }
    }
}
