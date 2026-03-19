# Private: QaDrainAndDeliverUseCase Implementation

## Status: COMPLETE

## Files Created
- `app/src/main/kotlin/com/glassthought/shepherd/core/server/AckedPayloadSender.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/question/QuestionAndAnswer.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/question/QaAnswersFileWriter.kt`
- `app/src/main/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCase.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/question/QaAnswersFileWriterTest.kt`
- `app/src/test/kotlin/com/glassthought/shepherd/core/question/QaDrainAndDeliverUseCaseTest.kt`

## Notes
- asgard logging uses `com.asgard.core.out.OutFactory`, `com.asgard.core.data.value.Val`, `com.asgard.core.data.value.ValType`
- Method is `getOutForClass()` not `forClass()`
- ValType is an enum; used `ValType.STRING_USER_AGNOSTIC` for all structured log values
- Two UserQuestionContext classes exist in different packages; mapping function added in companion object
