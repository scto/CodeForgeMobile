;;; Tree-sitter query for Kotlin scope resolution and local definitions/references.
;;; Format: Sora-Editor / AndroidIDE TreeSitter (LocalsCaptureSpec.DEFAULT):
;;; - @local.scope: Block defining a lexical scope (functions, lambdas, loops, blocks).
;;; - @local.scope.members: Class/object body defining member scopes.
;;; - @local.definition: Definition of a local variable or parameter.
;;; - @local.reference: Reference/usage of a variable or identifier.

;; =============================================================================
;; Scopes
;; =============================================================================

[
  (function_declaration)
  (secondary_constructor)
  (anonymous_initializer)
  (lambda_literal)
  (for_statement)
  (while_statement)
  (do_while_statement)
  (catch_block)
  (control_structure_body)
  (when_entry)
] @local.scope

(class_body) @local.scope.members

;; =============================================================================
;; Definitions (Parameters & Local Variables)
;; =============================================================================

; Function & constructor parameters
(parameter
  (simple_identifier) @local.definition)

(parameter_with_optional_type
  (simple_identifier) @local.definition)

; Lambda parameters
(lambda_literal
  (lambda_parameters
    (variable_declaration
      (simple_identifier) @local.definition)))

; Local variable declarations (val / var)
(property_declaration
  (variable_declaration
    (simple_identifier) @local.definition))

; For-loop variable definitions
(for_statement
  (variable_declaration
    (simple_identifier) @local.definition))

(for_statement
  (simple_identifier) @local.definition)

; Catch block exception variable
(catch_block
  (simple_identifier) @local.definition)

;; =============================================================================
;; References
;; =============================================================================

(simple_identifier) @local.reference
