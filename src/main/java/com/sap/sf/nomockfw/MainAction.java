package com.sap.sf.nomockfw;

import com.github.javaparser.JavaParser;
import com.github.javaparser.Position;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.successfactors.tool.codegen.common.utils.CodeGenUtil;
import com.successfactors.tool.codegen.common.utils.FileUtil;
import com.successfactors.tool.codegen.handlers.java.JavaHelper;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class MainAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        try {
            generateStub(anActionEvent);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

    }

    private void generateStub(AnActionEvent anActionEvent) throws Exception {
        VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(anActionEvent.getData(LangDataKeys.EDITOR).getDocument());

        File file = new File(virtualFile.getPath());

        parser(file);

    }

    private void parser(File inFile) throws Exception {
            CompilationUnit unit = StaticJavaParser.parse(inFile);

            VoidVisitor<List<MethodDeclaration>> methodNameCollector = new MethodNameCollector();
            List<MethodDeclaration> methodNames = new ArrayList<>();
            methodNameCollector.visit(unit, methodNames);

            createJavaClass(unit, methodNames);
    }


    private void createJavaClass(CompilationUnit existingFile, List<MethodDeclaration> methodNames) throws IOException {

        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(existingFile.getPackageDeclaration().get());

        List<String> className = new ArrayList<String>();
        VoidVisitor<List<String>> classNameCollector = new ClassNameCollector();
        classNameCollector.visit(existingFile, className);
        ClassOrInterfaceDeclaration finalClass = cu.addClass(className.get(0)+"Stub");

        cu.addImport("com.successfactors.unittest.dsl.api.mock.IInvocationHandler");
        cu.addImport("com.successfactors.unittest.dsl.mock.DslMock");
        cu.addImport("com.successfactors.unittest.dsl.mock.MethodObjectGenerator");
        cu.addImport("java.lang.reflect.Method");

        finalClass.addPrivateField("IInvocationHandler", "invocationHandler");

        finalClass.addConstructor(Modifier.Keyword.PUBLIC)
                .addParameter("IInvocationHandler", "invocationHandler")
                .setBody(new BlockStmt()
                        .addStatement(new ExpressionStmt(new AssignExpr(
                                new FieldAccessExpr(new ThisExpr(), "invocationHandler"),
                                new NameExpr("invocationHandler"),
                                AssignExpr.Operator.ASSIGN))));

        String pathname = "C:/Users/I513559/Desktop/_temp/" + className.get(0) + "Stub.java";
        File file = new File(pathname);
        FileUtil.createNewFile(file);
        FileUtil.writeToFile(file, Arrays.asList(cu.toString()));

        //methods
        for (MethodDeclaration methodName :
                methodNames) {
            int lineNumber = ((Position)getClassByName(file).getEnd().get()).line - 1;
            String method1 = "\n\t@Override\n" +
                    "\t"+ methodName.getDeclarationAsString() +" {\n" +
                    "\t\ttry {\n" +
                    "\t\t\tMethod me = (new MethodObjectGenerator() {\n" +
                    "\t\t\t}).getMethod();\n" +
                    "\t\t\tObject returnObject = invocationHandler.handle(DslMock.generateInvocation(this, me));\n" +
                    "\t\t\tif (!invocationHandler.isInvokeRealMethod(returnObject)) {\n" +
                    "\t\t\t\treturn (String) returnObject;\n" +
                    "\t\t\t}\n\t\t} ";
            if (!CollectionUtils.isEmpty(methodName.getThrownExceptions())) {
                for (ReferenceType referenceType:
                     methodName.getThrownExceptions()) {
                    method1 = method1 + "catch ("+ referenceType.asString() +" ex) {\n" +
                            "\t\t\tthrow ex;\n" + "\t\t}\n\t\t";
                }
            }
            method1 = method1 + "catch (Throwable ex) {\n" +
                    "\t\t\tinvocationHandler.handleUnexpectedException(ex);\n" +
                    "\t\t}\n" +
                    "\t\treturn super."+methodName.getNameAsString()+"(";
            for (Parameter parameter :
                    methodName.getParameters()) {
                method1 = method1 + parameter.getNameAsString();
            }
            method1 = method1 + ");\n\t}";
            FileUtil.updateFileContent(pathname, method1, lineNumber);
        }
    }

    protected ClassOrInterfaceDeclaration getClassByName(File file) throws FileNotFoundException {
        String baseName = CodeGenUtil.getBaseName(file.getName());
        ClassOrInterfaceDeclaration classDeclaration = null;
        CompilationUnit cu = StaticJavaParser.parse(file);
        try {
            classDeclaration = (ClassOrInterfaceDeclaration)cu.getClassByName(baseName).get();
        } catch (Exception var4) {
            classDeclaration = (ClassOrInterfaceDeclaration)cu.getInterfaceByName(baseName).get();
        }

        return classDeclaration;
    }
}
