package com.jack.compiler;

import com.google.auto.common.SuperficialValidation;
import com.google.auto.service.AutoService;
import com.jack.anno.BindView;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
public class BindViewProcessor extends AbstractProcessor {

    private Elements elements;
    private Types types;
    private Filer filer;

    private static final ClassName VIEW_BINDER = ClassName.get("com.jacksen.jackanno", "ViewBinder");

    private static final String BINDING_CLASS_SUFFIX = "$$ViewBinder";

    @Override
    public synchronized void init(ProcessingEnvironment environment) {
        super.init(environment);
        //
        elements = environment.getElementUtils();
        types = environment.getTypeUtils();
        filer = environment.getFiler();

    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(BindView.class.getCanonicalName());
        return types;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {

        Map<TypeElement, List<FieldViewBinding>> targetClassMap = new LinkedHashMap<>();

        Set<? extends Element> elementSet = roundEnv.getElementsAnnotatedWith(BindView.class);

        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, elementSet.size() + "---" + elementSet.iterator().next().getSimpleName());

        for (Element element : elementSet) {
            if (!SuperficialValidation.validateElement(element)) {
                continue;
            }
            TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
            boolean hasError = isInaccessibleViaGeneratedCode(BindView.class, "fields", element) || isBingingInWrongPackage(BindView.class, element);

            //
            TypeMirror elementType = element.asType();
            if (elementType.getKind() == TypeKind.TYPEVAR) {
                TypeVariable typeVariable = (TypeVariable) elementType;
                elementType = typeVariable.getUpperBound();
            }

            if (!isSubtypeOfType(elementType, "android.view.View") && !isInterface(elementType)) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s fields must extend from View or be an interface. (%s.%s)",
                        BindView.class.getSimpleName(), enclosingElement.getQualifiedName(), element.getSimpleName()));
                hasError = true;
            }
            if (hasError) {
                continue;
            }

            //
            List<FieldViewBinding> fieldViewBindingList = targetClassMap.get(enclosingElement);
            if (fieldViewBindingList == null) {
                fieldViewBindingList = new ArrayList<>();
                targetClassMap.put(enclosingElement, fieldViewBindingList);
            }

            String packageName = getPackageName(enclosingElement);
            TypeName targetType = TypeName.get(enclosingElement.asType());
            int id = element.getAnnotation(BindView.class).value();
            String fieldName = element.getSimpleName().toString();
            TypeMirror fieldType = element.asType();

            FieldViewBinding fieldViewBinding = new FieldViewBinding(id, fieldName, fieldType);
            fieldViewBindingList.add(fieldViewBinding);

        }

        // 遍历创建java文件
        for (Map.Entry<TypeElement, List<FieldViewBinding>> item : targetClassMap.entrySet()) {
            List<FieldViewBinding> list = item.getValue();
            if (list == null || list.size() == 0) {
                continue;
            }


            TypeElement enclosingElement = item.getKey();
            String packageName = getPackageName(enclosingElement);
            ClassName typeClassName = ClassName.bestGuess(getClassName(enclosingElement, packageName));

            //create class
            TypeSpec.Builder result = TypeSpec.classBuilder(getClassName(enclosingElement, packageName) + BINDING_CLASS_SUFFIX)
                    .addModifiers(Modifier.PUBLIC)
                    .addTypeVariable(TypeVariableName.get("T", typeClassName))
                    .addSuperinterface(ParameterizedTypeName.get(VIEW_BINDER, typeClassName));
            result.addMethod(createBindMethod(list, typeClassName));

            //创建java文件，并写入内容
            try {
                JavaFile.builder(packageName, result.build())
                        .addFileComment("This codes are generated automatically. Do not modify! ")
                        .build().writeTo(filer);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        return true;
    }


    /**
     * create method for bind
     *
     * @param list
     * @param typeClassName
     * @return
     */
    private MethodSpec createBindMethod(List<FieldViewBinding> list, ClassName typeClassName) {
        MethodSpec.Builder result = MethodSpec.methodBuilder("bind")
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.VOID)
                .addAnnotation(Override.class)
                .addParameter(typeClassName, "target", Modifier.FINAL);
        for (int i = 0; i < list.size(); i++) {
            FieldViewBinding fieldViewBinding = list.get(i);
            String packageString = fieldViewBinding.getTypeMirror().toString();
            ClassName viewClass = ClassName.bestGuess(packageString);
            result.addStatement("target.$L = ($T)target.findViewById($L)", fieldViewBinding.getName(), viewClass, fieldViewBinding.getResId());
        }
        return result.build();
    }


    /**
     * @param annotationClass
     * @param targetThing
     * @param element
     * @return
     */
    private boolean isInaccessibleViaGeneratedCode(Class<? extends Annotation> annotationClass, String targetThing, Element element) {
        boolean hasError = false;
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        Set<Modifier> modifiers = element.getModifiers();
        if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s %s must not be private or static. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(), element.getSimpleName()));
            hasError = true;
        }

        //
        if (enclosingElement.getKind() != ElementKind.CLASS) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s %s may only be contained in classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(), element.getSimpleName()));
            hasError = true;
        }

        //
        if (enclosingElement.getModifiers().contains(Modifier.PRIVATE)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s %s may not be contained in private classes. (%s.%s)",
                    annotationClass.getSimpleName(), targetThing, enclosingElement.getQualifiedName(), element.getSimpleName()));
            hasError = true;
        }
        return hasError;
    }

    /**
     * @param annotationClass
     * @param element
     * @return
     */
    private boolean isBingingInWrongPackage(Class<? extends Annotation> annotationClass, Element element) {
        TypeElement enclosingElement = (TypeElement) element.getEnclosingElement();
        String qualifiedName = enclosingElement.getQualifiedName().toString();

        if (qualifiedName.startsWith("android.")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s-annotated class incorrectly in Android framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName));
            return true;
        }
        if (qualifiedName.startsWith("java.")) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("@%s-annotated class incorrectly in Java framework package. (%s)",
                    annotationClass.getSimpleName(), qualifiedName));
            return true;
        }
        return false;
    }

    private boolean isSubtypeOfType(TypeMirror typeMirror, String otherType) {
        if (otherType.equals(typeMirror.toString())) {
            return true;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > 0) {
            StringBuilder sb = new StringBuilder(declaredType.asElement().toString());
            sb.append('<');
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append('?');
            }
            sb.append('>');
            if (sb.toString().equals(otherType)) {
                return true;
            }
        }
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        TypeMirror superType = typeElement.getSuperclass();
        if (isSubtypeOfType(superType, otherType)) {
            return true;
        }

        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (isSubtypeOfType(interfaceType, otherType)) {
                return true;
            }
        }
        return false;
    }


    /**
     * @param typeMirror
     * @return
     */
    private boolean isInterface(TypeMirror typeMirror) {
        return typeMirror instanceof DeclaredType &&
                ((DeclaredType) typeMirror).asElement().getKind() == ElementKind.INTERFACE;
    }

    private String getPackageName(TypeElement type) {
        return elements.getPackageOf(type).getQualifiedName().toString();
    }

    private static String getClassName(TypeElement type, String packageName) {
        int packageLen = packageName.length() + 1;
        return type.getQualifiedName().toString().substring(packageLen).replace('.', '$');
    }


}
