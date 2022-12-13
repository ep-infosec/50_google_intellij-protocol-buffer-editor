/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devtools.intellij.protoeditor.java;

import com.google.common.collect.ImmutableSet;
import com.google.devtools.intellij.protoeditor.java.names.NameGenerator;
import com.google.devtools.intellij.protoeditor.java.names.NameGeneratorSelector;
import com.google.devtools.intellij.protoeditor.lang.psi.PbEnumDefinition;
import com.google.devtools.intellij.protoeditor.lang.psi.PbEnumValue;
import com.google.devtools.intellij.protoeditor.lang.psi.PbField;
import com.google.devtools.intellij.protoeditor.lang.psi.PbFile;
import com.google.devtools.intellij.protoeditor.lang.psi.PbGroupDefinition;
import com.google.devtools.intellij.protoeditor.lang.psi.PbMessageDefinition;
import com.google.devtools.intellij.protoeditor.lang.psi.PbMessageType;
import com.google.devtools.intellij.protoeditor.lang.psi.PbNamedElement;
import com.google.devtools.intellij.protoeditor.lang.psi.PbOneofDefinition;
import com.google.devtools.intellij.protoeditor.lang.psi.PbStatementOwner;
import com.google.devtools.intellij.protoeditor.lang.psi.PbSymbol;
import com.google.devtools.intellij.protoeditor.lang.psi.PbSymbolOwner;
import com.google.devtools.intellij.protoeditor.lang.psi.PbVisitor;
import com.google.devtools.intellij.protoeditor.lang.psi.util.PbPsiUtil;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link FindUsagesHandlerFactory} for proto elements. Returns Java elements corresponding to
 * generated code, which will then become additional search targets.
 */
public class PbJavaFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

  @Override
  public boolean canFindUsages(@NotNull PsiElement psiElement) {
    return psiElement instanceof PbSymbol;
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler(
      @NotNull PsiElement psiElement, boolean forHighlightUsages) {
    // This factory only handles proto -> java.
    // When highlighting usages of proto elements, the editor will have a proto file open,
    // so there's no chance that java elements will be useful in that same editor.
    if (forHighlightUsages) {
      return null;
    }
    if (!(psiElement instanceof PbSymbol)) {
      return null;
    }
    PbSymbol symbol = ((PbSymbol) psiElement);
    PbFile file = symbol.getPbFile();
    ProtoToJavaConverter dispatcher = new ProtoToJavaConverter(file);
    symbol.accept(dispatcher);
    if (dispatcher.results == null) {
      return null;
    }
    return new AdditionalUsagesHandler(symbol, dispatcher.results);
  }

  /** A handler that supplies Java elements on top of the PbSymbol. */
  private static class AdditionalUsagesHandler extends FindUsagesHandler {
    private final PsiElement[] additionalElements;

    AdditionalUsagesHandler(@NotNull PbSymbol element, PsiElement... additionalElements) {
      super(element);
      this.additionalElements = additionalElements;
    }

    @NotNull
    @Override
    public PsiElement[] getSecondaryElements() {
      return additionalElements;
    }
  }

  /** A visitor that converts certain proto PsiElements to the java elements it generates. */
  private static class ProtoToJavaConverter extends PbVisitor {
    PsiElement[] results;
    private final PbFile file;

    // Pretty unlikely that a base class will have the java class members we are interested in,
    // since they are supposed to be customized to the proto field name. Be careful though,
    // non-specialized members could live in a base class for the sake of smaller code size.
    private static final boolean CHECK_BASES = false;

    private NotNullLazyValue<List<NameGenerator>> nameGenerators =
        new NotNullLazyValue<List<NameGenerator>>() {
          @NotNull
          @Override
          protected List<NameGenerator> compute() {
            return NameGeneratorSelector.selectForFile(file);
          }
        };

    ProtoToJavaConverter(PbFile file) {
      this.file = file;
    }

    private void setResults(Collection<? extends PsiElement> javaElements) {
      if (!javaElements.isEmpty()) {
        results = javaElements.stream().distinct().toArray(PsiElement[]::new);
      }
    }

    @Override
    public void visitMessageDefinition(PbMessageDefinition message) {
      setResults(messageTypeClasses(message));
    }

    @Override
    public void visitField(PbField field) {
      Collection<PsiMember> javaElements = fieldMembers(field);
      PbStatementOwner owner = field.getStatementOwner();
      if (PbPsiUtil.isOneofElement(owner)) {
        Collection<PsiClass> enumParents = oneofEnumClasses((PbOneofDefinition) owner);
        javaElements.addAll(oneofFieldEnumConstants(field, enumParents));
      }
      setResults(javaElements);
    }

    @Override
    public void visitGroupDefinition(PbGroupDefinition group) {
      Collection<PsiElement> javaElements = new ArrayList<>();
      javaElements.addAll(messageTypeClasses(group));
      // Groups are a message type + a field.  Also check for generated code related to the field.
      for (PbSymbol sibling : group.getAdditionalSiblings()) {
        ProtoToJavaConverter siblingVisitor = new ProtoToJavaConverter(file);
        sibling.accept(siblingVisitor);
        if (siblingVisitor.results != null) {
          Collections.addAll(javaElements, siblingVisitor.results);
        }
      }
      setResults(javaElements);
    }

    @Override
    public void visitEnumDefinition(PbEnumDefinition enumDefinition) {
      setResults(enumDefinitionClasses(enumDefinition));
    }

    @Override
    public void visitEnumValue(PbEnumValue enumValue) {
      setResults(enumValueEnumConstants(enumValue));
    }

    @Override
    public void visitOneofDefinition(PbOneofDefinition oneof) {
      // Get the message class members
      Collection<PsiElement> javaElements = new ArrayList<>();
      javaElements.addAll(oneofMessageMembers(oneof));
      // Get the java enum
      Collection<PsiClass> oneofEnums = oneofEnumClasses(oneof);
      javaElements.addAll(oneofEnums);
      // Get the not-set enum value.
      javaElements.addAll(
          protoToEnumConstants(oneof, NameGenerator::oneofNotSetEnumValueName, oneofEnums));
      setResults(javaElements);
    }

    private Collection<PsiClass> messageTypeClasses(PbMessageType messageType) {
      return protoToClasses(messageType, NameGenerator::messageClassNames, (cls) -> !cls.isEnum());
    }

    private Collection<PsiClass> enumDefinitionClasses(PbEnumDefinition enumDefinition) {
      return protoToClasses(
          enumDefinition,
          (nameGen, enumDef) -> {
            String name = nameGen.enumClassName(enumDef);
            return name != null ? ImmutableSet.of(name) : ImmutableSet.of();
          },
          PsiClass::isEnum);
    }

    private Collection<PsiClass> oneofEnumClasses(PbOneofDefinition oneof) {
      return protoToClasses(
          oneof,
          (nameGen, oneofDef) -> {
            String name = nameGen.oneofEnumClassName(oneofDef);
            return name != null ? ImmutableSet.of(name) : ImmutableSet.of();
          },
          PsiClass::isEnum);
    }

    private <ProtoT extends PbNamedElement> Collection<PsiClass> protoToClasses(
        ProtoT protoNamedType,
        BiFunction<NameGenerator, ProtoT, Set<String>> toClassNames,
        Predicate<PsiClass> classPredicate) {
      List<PsiClass> javaElements = new ArrayList<>();
      JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(file.getProject());
      GlobalSearchScope scope = getScope(file);
      for (NameGenerator generator : nameGenerators.getValue()) {
        Set<String> classNames = toClassNames.apply(generator, protoNamedType);
        for (String className : classNames) {
          PsiClass[] classes = javaPsiFacade.findClasses(className, scope);
          Arrays.stream(classes).filter(classPredicate).forEach(javaElements::add);
        }
      }
      return javaElements;
    }

    private Collection<PsiMember> fieldMembers(PbField field) {
      return protoToMembers(field, NameGenerator::fieldMemberNames);
    }

    private Collection<PsiMember> oneofMessageMembers(PbOneofDefinition oneof) {
      return protoToMembers(oneof, NameGenerator::oneofMemberNames);
    }

    private <ProtoT extends PbNamedElement> Collection<PsiMember> protoToMembers(
        ProtoT protoElement, BiFunction<NameGenerator, ProtoT, Set<String>> toMemberNames) {
      PbSymbolOwner owner = protoElement.getSymbolOwner();
      if (!PbPsiUtil.isMessageElement(owner)) {
        return Collections.emptyList();
      }
      PbMessageType message = (PbMessageType) owner;
      Collection<PsiClass> parentClasses = messageTypeClasses(message);
      if (parentClasses.isEmpty()) {
        return Collections.emptyList();
      }
      List<PsiMember> javaElements = new ArrayList<>();
      for (NameGenerator generator : nameGenerators.getValue()) {
        Collection<String> fieldMembers = toMemberNames.apply(generator, protoElement);
        for (PsiClass psiClass : parentClasses) {
          for (String qualifiedMember : fieldMembers) {
            // qualifiedMember might be something like "Builder.setFoo", so look up the
            // Builder inner class first, if needed.
            String[] components = qualifiedMember.split("\\.");
            int componentIndex = 0;
            PsiClass parentClass = psiClass;
            while ((componentIndex < components.length - 1) && parentClass != null) {
              String innerClass = components[componentIndex];
              parentClass = parentClass.findInnerClassByName(innerClass, CHECK_BASES);
              ++componentIndex;
            }
            if (parentClass == null) {
              continue;
            }
            String fieldOrMethod = components[componentIndex];
            PsiField javaField = parentClass.findFieldByName(fieldOrMethod, CHECK_BASES);
            if (javaField != null) {
              javaElements.add(javaField);
            } else {
              PsiMethod[] methods = parentClass.findMethodsByName(fieldOrMethod, CHECK_BASES);
              Collections.addAll(javaElements, methods);
            }
          }
        }
      }
      return javaElements;
    }

    private Collection<PsiField> enumValueEnumConstants(PbEnumValue enumValue) {
      PbEnumDefinition enumDefinition =
          PsiTreeUtil.getParentOfType(enumValue, PbEnumDefinition.class);
      if (enumDefinition == null) {
        return Collections.emptyList();
      }
      Collection<PsiClass> enumParents = enumDefinitionClasses(enumDefinition);
      if (enumParents.isEmpty()) {
        return Collections.emptyList();
      }
      return protoToEnumConstants(enumValue, NameGenerator::enumValueName, enumParents);
    }

    private Collection<PsiField> oneofFieldEnumConstants(
        PbField oneofField, Collection<PsiClass> enumParents) {
      List<PsiField> javaElements = new ArrayList<>();
      javaElements.addAll(
          protoToEnumConstants(oneofField, NameGenerator::oneofEnumValueName, enumParents));
      return javaElements;
    }

    private <ProtoT extends PbNamedElement> Collection<PsiField> protoToEnumConstants(
        ProtoT protoElement,
        BiFunction<NameGenerator, ProtoT, String> toEnumName,
        Collection<PsiClass> enumParents) {
      List<PsiField> javaElements = new ArrayList<>();
      for (NameGenerator generator : nameGenerators.getValue()) {
        String enumValueName = toEnumName.apply(generator, protoElement);
        if (enumValueName == null) {
          continue;
        }
        for (PsiClass enumParent : enumParents) {
          PsiField javaEnumValue = enumParent.findFieldByName(enumValueName, CHECK_BASES);
          if (javaEnumValue instanceof PsiEnumConstant) {
            javaElements.add(javaEnumValue);
          }
        }
      }
      return javaElements;
    }

    private GlobalSearchScope getScope(PsiElement element) {
      Module module = ModuleUtilCore.findModuleForPsiElement(element);
      if (module != null) {
        return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
      }
      return GlobalSearchScope.allScope(element.getProject());
    }
  }
}
