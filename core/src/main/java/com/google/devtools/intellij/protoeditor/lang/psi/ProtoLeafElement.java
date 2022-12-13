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
package com.google.devtools.intellij.protoeditor.lang.psi;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link LeafPsiElement} extension that inherits its language from its parent element rather than
 * its token type. Necessary for protobuf and prototext files which share a lexer and a set of leaf
 * token types.
 */
public class ProtoLeafElement extends LeafPsiElement {

  public ProtoLeafElement(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }

  @Override
  @NotNull
  public Language getLanguage() {
    final PsiElement master = getParent();
    return master != null ? master.getLanguage() : Language.ANY;
  }
}
