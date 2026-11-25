# R8 rules for Progress 1 Percent.
#
# AndroidX, Compose, ML Kit and ONNX Runtime ship their own consumer rules.
# Keeping the whole application or dependency namespaces would disable useful
# shrinking and optimization, so only attributes needed for diagnostics and
# exception stack traces are retained here.

-optimizationpasses 5
-allowaccessmodification

-keepattributes Exceptions,InnerClasses,Signature,RuntimeVisibleAnnotations,AnnotationDefault,SourceFile,LineNumberTable
