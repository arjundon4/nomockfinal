package com.sap.sf.nomockfw;

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
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.successfactors.tool.codegen.common.utils.CodeGenUtil;
import com.successfactors.tool.codegen.common.utils.FileUtil;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
        try {
            VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(anActionEvent.getData(LangDataKeys.EDITOR).getDocument());
            File sourceFile = new File(virtualFile.getPath());
            generateStub(sourceFile);
        } catch (Exception exception) {
            exception.printStackTrace();
        }

    }

    private void generateStub(File inFile) throws Exception {
            CompilationUnit compilationUnit = StaticJavaParser.parse(inFile);

            //get public and protected methods
            VoidVisitor<List<MethodDeclaration>> methodNameCollector = new MethodNameCollector();
            List<MethodDeclaration> methodsToOverride = new ArrayList<>();
            methodNameCollector.visit(compilationUnit, methodsToOverride);

            //generate the Java Stub class
            createJavaStubClass(compilationUnit, methodsToOverride, inFile.getAbsolutePath());
    }


    private void createJavaStubClass(CompilationUnit existingFileCU, List<MethodDeclaration> methodsToOverride, String inFilePath) throws IOException {

        CompilationUnit stubFileCU = new CompilationUnit();
        stubFileCU.setPackageDeclaration(existingFileCU.getPackageDeclaration().get());

        //get class name
        List<String> className = new ArrayList<String>();
        VoidVisitor<List<String>> classNameCollector = new ClassNameCollector();
        classNameCollector.visit(existingFileCU, className);
        ClassOrInterfaceDeclaration finalClass = stubFileCU.addClass(className.get(0)+"Stub");
        //extend base class
        finalClass.addExtendedType(className.get(0));
        //add imports
        stubFileCU.addImport("com.successfactors.unittest.dsl.api.mock.IInvocationHandler");
        stubFileCU.addImport("com.successfactors.unittest.dsl.mock.DslMocker");
        stubFileCU.addImport("com.successfactors.unittest.dsl.mock.MethodObjectGenerator");
        stubFileCU.addImport("java.lang.reflect.Method");

        //add private field
        finalClass.addPrivateField("IInvocationHandler", "invocationHandler");

        //add constructor
        finalClass.addConstructor(Modifier.Keyword.PUBLIC)
                .addParameter("IInvocationHandler", "invocationHandler")
                .setBody(new BlockStmt()
                        .addStatement(new ExpressionStmt(new AssignExpr(
                                new FieldAccessExpr(new ThisExpr(), "invocationHandler"),
                                new NameExpr("invocationHandler"),
                                AssignExpr.Operator.ASSIGN))));

        //write to file
        String targetPath = inFilePath.replace("src"+File.separator+"main", "src"+File.separator+"test");
        targetPath = targetPath.replace(className.get(0), className.get(0)+"Stub");
        File targetFile = new File(targetPath);
        FileUtil.createNewFile(targetFile);
        FileUtil.writeToFile(targetFile, Arrays.asList(stubFileCU.toString()));

        //add methods by overriding the existing public and protected methods
        for (MethodDeclaration methodToOverride :
                methodsToOverride) {
            int methodPosition = ((Position)getClassByName(targetFile).getEnd().get()).line - 1;
            String methodString = "\n\t@Override\n" +
                    "\t"+ methodToOverride.getDeclarationAsString() +" {\n" +
                    "\t\ttry {\n" +
                    "\t\t\tMethod me = (new MethodObjectGenerator() {\n" +
                    "\t\t\t}).getMethod();\n" +
                    "\t\t\tObject returnObject = invocationHandler.handle(DslMocker.generateInvocation(this, me));\n" +
                    "\t\t\tif (!invocationHandler.isInvokeRealMethod(returnObject)) {\n";

            boolean isvoidType = methodToOverride.getType().isVoidType();
            if (isvoidType) {
                methodString = methodString + "\t\t\t\treturn;\n" +
                        "\t\t\t}\n\t\t} ";
            } else {
                methodString = methodString +
                        "\t\t\t\treturn ("+methodToOverride.getType().asString()+") returnObject;\n" +
                        "\t\t\t}\n\t\t} ";
            }

            if (!CollectionUtils.isEmpty(methodToOverride.getThrownExceptions())) {
                for (ReferenceType referenceType:
                     methodToOverride.getThrownExceptions()) {
                    methodString = methodString + "catch ("+ referenceType.asString() +" ex) {\n" +
                            "\t\t\tthrow ex;\n" + "\t\t}\n\t\t";
                }
            }
            methodString = methodString + "catch (Throwable ex) {\n" +
                    "\t\t\tinvocationHandler.handleUnexpectedException(ex);\n" +
                    "\t\t}\n";
            if(isvoidType) {
                methodString = methodString + "\t\tsuper."+methodToOverride.getNameAsString()+"(";
            } else {
                methodString = methodString + "\t\treturn super."+methodToOverride.getNameAsString()+"(";
            }

            for (int i = 0; i<methodToOverride.getParameters().size(); i++) {
                if(i==0)
                    methodString = methodString + methodToOverride.getParameters().get(i).getNameAsString();
                else
                    methodString = methodString + ", " + methodToOverride.getParameters().get(i).getNameAsString();
            }
            methodString = methodString + ");\n\t}";
            FileUtil.updateFileContent(targetPath, methodString, methodPosition);
        }
    }

    private ClassOrInterfaceDeclaration getClassByName(File file) throws FileNotFoundException {
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
