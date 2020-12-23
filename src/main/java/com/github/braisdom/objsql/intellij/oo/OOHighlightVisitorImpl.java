/* Copyright 2013 Artem Melentyev <amelentev@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.braisdom.objsql.intellij.oo;

import com.github.braisdom.objsql.intellij.ObjSqlPsiAugmentProvider;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightVisitorImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.braisdom.objsql.intellij.oo.Util.sneakyThrow;

public class OOHighlightVisitorImpl extends HighlightVisitorImpl {

    private static final Logger LOGGER = Logger.getInstance(ObjSqlPsiAugmentProvider.class.getName());

    private HighlightInfoHolder myHolder;
    private PsiResolveHelper resolveHelper;

    protected OOHighlightVisitorImpl(@NotNull PsiResolveHelper resolveHelper) {
        this.resolveHelper = resolveHelper;
    }

    @Override
    public boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable action) {
        myHolder = holder;
        try {
            return super.analyze(file, updateWholeFile, holder, action);
        } finally {
            myHolder = null;
        }
    }

    @Override
    public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
    }

    @Override // Binary OO
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
        super.visitPolyadicExpression(expression);
        if (isHighlighted(expression)) {
            PsiExpression[] operands = expression.getOperands();
            PsiType lType = operands[0].getType();
            for (int i = 1; i < operands.length; i++) {
                PsiExpression operand = operands[i];
                PsiType rType = operand.getType();
                // TODO: case A + A + int ? A.add(A) : int
                lType = OOResolver.getOOType(lType, rType, expression.getTokenBeforeOperand(operand));
            }
            if (lType != OOResolver.NoType)
                removeLastHighlight();
            else LOGGER.info("visitPolyadicExpression lType = OOResolver.NoType, lType:" + lType);
        } else LOGGER.info("visitPolyadicExpression ignore highlihted");
    }

    @Override // Unary OO
    public void visitPrefixExpression(PsiPrefixExpression expression) {
        super.visitPrefixExpression(expression);
        if (isHighlighted(expression)
                && OOResolver.getOOType(expression) != OOResolver.NoType) {
            removeLastHighlight();
        } else LOGGER.info("visitPrefixExpression ignore highlihted, Highlighted: "
                + isHighlighted(expression) + ", OOType: " + OOResolver.getOOType(expression));
    }

    @Override // Index-Get OO
    public void visitExpression(PsiExpression expression) {
        super.visitExpression(expression);
        if (expression instanceof PsiArrayAccessExpression) {
            PsiArrayAccessExpression paa = (PsiArrayAccessExpression) expression;
            if (isHighlighted(paa.getArrayExpression())
                    && OOResolver.indexGet((PsiArrayAccessExpression) expression) != OOResolver.NoType)
                removeLastHighlight();
            else LOGGER.info("visitExpression ignore highlihted, Highlighted: "
                    + isHighlighted(paa.getArrayExpression()) + ", OOType: " + OOResolver.indexGet((PsiArrayAccessExpression) expression));
        }
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression ass) {
        super.visitAssignmentExpression(ass);
        if ("=".equals(ass.getOperationSign().getText())) {
            // Index-Set OO
            if (ass.getLExpression() instanceof PsiArrayAccessExpression
                    && isHighlighted(ass.getLExpression())) {
                PsiArrayAccessExpression paa = (PsiArrayAccessExpression) ass.getLExpression();
                if (OOResolver.indexSet(paa, ass.getRExpression()) != OOResolver.NoType)
                    removeLastHighlight();
                else LOGGER.info("visitAssignmentExpression ignore highlihted, OOType: "
                        + OOResolver.indexSet(paa, ass.getRExpression()));
            } else LOGGER.info("visitAssignmentExpression ignore highlihted, Highlighted: "
                    + isHighlighted(ass.getLExpression()));
            // Implicit type conversion in assignment
            if (isHighlighted(ass) && OOResolver.isTypeConvertible(ass.getLExpression().getType(), ass.getRExpression()))
                removeLastHighlight();
            else LOGGER.info("visitAssignmentExpression ignore highlihted, Highlighted: "
                    + isHighlighted(ass) + ", isTypeConvertible: " + OOResolver.isTypeConvertible(ass.getLExpression().getType(), ass.getRExpression()));
        }
    }

    @Override // Implicit type conversion in variable declaration
    public void visitVariable(PsiVariable var) {
        super.visitVariable(var);
        if (var.hasInitializer() && isHighlighted(var) && OOResolver.isTypeConvertible(var.getType(), var.getInitializer()))
            removeLastHighlight();
        else LOGGER.info("visitVariable hasInitializer: " + var.hasInitializer()
                + ", isHighlighted: " + isHighlighted(var) + ", isTypeConvertible: " + OOResolver.isTypeConvertible(var.getType(), var.getInitializer()));
    }

    private boolean isHighlighted(@NotNull PsiElement expression) {
        if (myHolder.hasErrorResults()) {
            HighlightInfo hi = myHolder.get(myHolder.size() - 1);
            if (hi.getSeverity() != HighlightSeverity.ERROR) return false;
            if (expression instanceof PsiVariable) { // workaround for variable declaration incompatible types highlight
                PsiVariable v = (PsiVariable) expression;
                PsiTypeElement pte = v.getTypeElement();
                if (pte == null)
                    return false;
                TextRange tetr = pte.getTextRange();
                TextRange tr = v.getTextRange();
                return tr != null && tetr != null
                        && hi.startOffset == tetr.getStartOffset()
                        && hi.endOffset == tr.getEndOffset();
            }
            TextRange tr = expression.getTextRange();
            return hi.startOffset == tr.getStartOffset() && hi.endOffset == tr.getEndOffset();
        }
        return false;
    }

    // TODO: what highlightInfo to delete?
    private void removeLastHighlight() {
        // remove highlight
        List<HighlightInfo> myInfos = (List<HighlightInfo>) Util.get(HighlightInfoHolder.class, myHolder, List.class, "myInfos", "f");
        myInfos.remove(myHolder.size() - 1);
        // update error count
        Field fmyErrorCount = Util.findField(HighlightInfoHolder.class, int.class, "myErrorCount", "b");
        try {
            fmyErrorCount.setInt(myHolder, fmyErrorCount.getInt(myHolder) - 1);
        } catch (IllegalAccessException e) {
            throw sneakyThrow(e);
        }
    }

    @Override
    @NotNull
    public OOHighlightVisitorImpl clone() {
        return new OOHighlightVisitorImpl(resolveHelper);
    }
}
