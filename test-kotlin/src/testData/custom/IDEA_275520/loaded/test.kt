/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package testData.custom.IDEA_275520.loaded

import testData.custom.IDEA_275520.nestedInlines
import testData.custom.IDEA_275520.oneLineInline
import testData.custom.IDEA_275520.simpleInline
import testData.custom.IDEA_275520.testWithLambda

fun main() {
    simpleInline(3)
    nestedInlines(3)
    oneLineInline()
    testWithLambda()
    Class.forName("testData.custom.IDEA_275520.Test2Kt")
}
