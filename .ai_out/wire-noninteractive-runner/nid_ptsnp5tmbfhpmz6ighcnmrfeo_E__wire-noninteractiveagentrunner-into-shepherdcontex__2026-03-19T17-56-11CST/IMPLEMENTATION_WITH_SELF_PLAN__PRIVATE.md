# Private Implementation Notes

## Status: COMPLETE

All steps done, all tests green.

## Decisions
- Caught `java.io.IOException` instead of generic `Exception` to satisfy detekt's TooGenericExceptionCaught rule
- Used the same injectable-defaults pattern as EnvironmentValidatorImpl for testability seams
- ZAI API key is trimmed after reading to handle trailing newlines in the file
