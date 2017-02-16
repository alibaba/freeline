package com.antfortune.freeline.idea.utils;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Created by pengwei on 2016/10/31.
 */
public class GroovyFileUil {

    /**
     * 获取最后一个插件的表达式
     *
     * @param buildScript
     * @return
     */
    public static GrExpression getLastPlugin(GroovyFile buildScript) {
        Iterator var2 = getMethodCalls(buildScript, "apply").iterator();
        GrExpression expression = null;
        while (var2.hasNext()) {
            GrMethodCall methodCall = (GrMethodCall) var2.next();
            expression = methodCall.getInvokedExpression();
        }
        return expression;
    }

    public static Iterable<GrMethodCall> getMethodCalls(@NotNull GrStatementOwner parent) {
        return Iterables.filter(Arrays.asList(parent.getStatements()), GrMethodCall.class);
    }

    public static Iterable<GrMethodCall> getMethodCalls(@NotNull GrStatementOwner parent, @NotNull final String methodName) {
        return Iterables.filter(getMethodCalls(parent), new Predicate<GrMethodCall>() {
            public boolean apply(@Nullable GrMethodCall input) {
                return input != null && methodName.equals(getMethodCallName(input));
            }
        });
    }

    public static String getMethodCallName(@NotNull GrMethodCall gmc) {
        GrExpression expression = gmc.getInvokedExpression();
        return expression.getText() != null ? expression.getText() : "";
    }

}
