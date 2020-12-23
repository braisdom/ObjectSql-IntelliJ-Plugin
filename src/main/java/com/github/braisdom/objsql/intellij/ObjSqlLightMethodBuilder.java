package com.github.braisdom.objsql.intellij;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightTypeParameterListBuilder;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public class ObjSqlLightMethodBuilder extends LightMethodBuilder {
    private PsiMethod myMethod;
    private ASTNode myASTNode;

    public ObjSqlLightMethodBuilder(@NotNull PsiManager manager, @NotNull String name) {
        super(manager, name);
    }

    public ObjSqlLightMethodBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
        addModifier(modifier);
        return this;
    }

    public ObjSqlLightMethodBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String... modifiers) {
        for (String modifier : modifiers) {
            addModifier(modifier);
        }
        return this;
    }

    public ObjSqlLightMethodBuilder withMethodReturnType(PsiType returnType) {
        setMethodReturnType(returnType);
        return this;
    }

    @Override
    public void navigate(boolean requestFocus) {
        PsiClassUtil.navigate(getProject(), getContainingClass(), (psiClasses -> {
            for (PsiClass psiClass : psiClasses) {
                PsiMethod[] psiMethods = psiClass.getMethods();
                for (PsiMethod psiMethod : psiMethods) {
                    if (getName().equals(psiMethod.getName())) {
                        Navigatable descriptor = PsiNavigationSupport.getInstance().getDescriptor(psiMethod);
                        if (descriptor != null) {
                            descriptor.navigate(requestFocus);
                            return true;
                        }
                    }
                }
            }
            return false;
        }));
    }

    @Override
    public boolean canNavigate() {
        return PsiNavigationSupport.getInstance().canNavigate(this);
    }

    @Override
    public boolean canNavigateToSource() {
        return false;
    }

    @Override
    public LightMethodBuilder setMethodReturnType(PsiType returnType) {
        return super.setMethodReturnType(returnType);
    }

    @Override
    public LightMethodBuilder setMethodReturnType(String returnType) {
        return super.setMethodReturnType(returnType);
    }

    public ObjSqlLightMethodBuilder withParameter(@NotNull String name, @NotNull PsiType type) {
        addParameter(name, type);
        return this;
    }

    public ObjSqlLightMethodBuilder withParameter(@NotNull String name, @NotNull String type) {
        addParameter(name, type);
        return this;
    }

    public ObjSqlLightMethodBuilder withParameter(@NotNull String name, @NotNull PsiType type, boolean isVarArgs) {
        addParameter(name, type, isVarArgs);
        return this;
    }

    public ObjSqlLightMethodBuilder withParameter(@NotNull String name, @NotNull String type, boolean isVarArgs) {
        addParameter(name, JavaPsiFacade.getElementFactory(getProject()).createTypeFromText(type, this), isVarArgs);
        return this;
    }

    public ObjSqlLightMethodBuilder withException(@NotNull PsiClassType type) {
        addException(type);
        return this;
    }

    public ObjSqlLightMethodBuilder withContainingClass(@NotNull PsiClass containingClass) {
        setContainingClass(containingClass);
        return this;
    }

    // add Parameter as is, without wrapping with LightTypeParameter
    @Override
    public LightMethodBuilder addTypeParameter(PsiTypeParameter parameter) {
        ((LightTypeParameterListBuilder) getTypeParameterList()).addParameter(parameter);
        return this;
    }

    @Override
    public PsiElement getParent() {
        PsiElement result = super.getParent();
        result = null != result ? result : getContainingClass();
        return result;
    }

    @Nullable
    @Override
    public PsiFile getContainingFile() {
        PsiClass containingClass = getContainingClass();
        return containingClass != null ? containingClass.getContainingFile() : null;
    }

    @Override
    public String getText() {
        ASTNode node = getNode();
        if (null != node) {
            return node.getText();
        }
        return "";
    }

    @Override
    public ASTNode getNode() {
        if (null == myASTNode) {
            final PsiElement myPsiMethod = getOrCreateMyPsiMethod();
            myASTNode = null == myPsiMethod ? null : myPsiMethod.getNode();
        }
        return myASTNode;
    }

    @Override
    public TextRange getTextRange() {
        TextRange r = super.getTextRange();
        return r == null ? TextRange.EMPTY_RANGE : r;
    }

    private String getAllModifierProperties(LightModifierList modifierList) {
        final StringBuilder builder = new StringBuilder();
        for (String modifier : modifierList.getModifiers()) {
            if (!PsiModifier.PACKAGE_LOCAL.equals(modifier)) {
                builder.append(modifier).append(' ');
            }
        }
        return builder.toString();
    }

    private PsiMethod rebuildMethodFromString() {
        PsiMethod result;
        try {
            final StringBuilder methodTextDeclaration = new StringBuilder();
            methodTextDeclaration.append(getAllModifierProperties((LightModifierList) getModifierList()));
            PsiType returnType = getReturnType();
            if (null != returnType && returnType.isValid()) {
                methodTextDeclaration.append(returnType.getCanonicalText()).append(' ');
            }
            methodTextDeclaration.append(getName());
            methodTextDeclaration.append('(');
            if (getParameterList().getParametersCount() > 0) {
                for (PsiParameter parameter : getParameterList().getParameters()) {
                    methodTextDeclaration.append(parameter.getType().getCanonicalText()).append(' ').append(parameter.getName()).append(',');
                }
                methodTextDeclaration.deleteCharAt(methodTextDeclaration.length() - 1);
            }
            methodTextDeclaration.append(')');
            methodTextDeclaration.append('{').append("  ").append('}');

            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(getManager().getProject());

            result = elementFactory.createMethodFromText(methodTextDeclaration.toString(), getContainingClass());
            if (null != getBody()) {
                result.getBody().replace(getBody());
            }
        } catch (Exception ex) {
            result = null;
        }
        return result;
    }

    @Override
    public PsiElement copy() {
        final PsiElement myPsiMethod = getOrCreateMyPsiMethod();
        return null == myPsiMethod ? null : myPsiMethod.copy();
    }

    private PsiElement getOrCreateMyPsiMethod() {
        if (null == myMethod) {
            myMethod = rebuildMethodFromString();
        }
        return myMethod;
    }

    @NotNull
    @Override
    public PsiElement[] getChildren() {
        final PsiElement myPsiMethod = getOrCreateMyPsiMethod();
        return null == myPsiMethod ? PsiElement.EMPTY_ARRAY : myPsiMethod.getChildren();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "#" + getName();
    }

    @Override
    public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
        // just add new element to the containing class
        final PsiClass containingClass = getContainingClass();
        if (null != containingClass) {
            CheckUtil.checkWritable(containingClass);
            return containingClass.add(newElement);
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof PsiMethod)) {
            return false;
        }

        PsiMethod that = (PsiMethod) o;
        if (!getName().equals(that.getName())) {
            return false;
        }
        if (isConstructor() != that.isConstructor()) {
            return false;
        }
        final PsiClass containingClass = getContainingClass();
        final PsiClass thatContainingClass = that.getContainingClass();
        if (containingClass != null ? !containingClass.equals(thatContainingClass) : thatContainingClass != null) {
            return false;
        }
        if (!getModifierList().equals(that.getModifierList())) {
            return false;
        }
        if (!getParameterList().equals(that.getParameterList())) {
            return false;
        }
        return Objects.equals(getReturnType().getPresentableText(), that.getReturnType().getPresentableText());
    }

    @Override
    public int hashCode() {
        // should be constant because of RenameJavaMethodProcessor#renameElement and fixNameCollisionsWithInnerClassMethod(...)
        return 1;
    }

    @Override
    public void delete() throws IncorrectOperationException {
        // simple do nothing
    }

    @Override
    public void checkDelete() throws IncorrectOperationException {
        // simple do nothing
    }
}
