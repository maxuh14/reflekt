package org.jetbrains.reflekt.test

import org.jetbrains.reflekt.Reflekt

fun main() {
    val objects = Reflekt.objects().withAnnotations<AInterfaceTest>(FirstAnnotationTest::class, SecondAnnotationTest::class)
}