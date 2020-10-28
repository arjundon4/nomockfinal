package com.sap.sf.nomockfw;

import java.util.List;

public class Method {
    String returnType;
    String methodName;
    List<Params> parameters;

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<Params> getParameters() {
        return parameters;
    }

    public void setParameters(List<Params> parameters) {
        this.parameters = parameters;
    }

    public List<String> getExceptionsThrown() {
        return exceptionsThrown;
    }

    public void setExceptionsThrown(List<String> exceptionsThrown) {
        this.exceptionsThrown = exceptionsThrown;
    }

    List<String> exceptionsThrown;

    private class Params {
        String paramName;
        String paramType;

        public String getParamName() {
            return paramName;
        }

        public void setParamName(String paramName) {
            this.paramName = paramName;
        }

        public String getParamType() {
            return paramType;
        }

        public void setParamType(String paramType) {
            this.paramType = paramType;
        }
    }
}
