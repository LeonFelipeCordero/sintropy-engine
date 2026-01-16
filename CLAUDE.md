## How to write SQL queries
* Indexes have ends with _idx, the pattern is table_field1_field2_idx

## JOOQ
* Don't use DSL unless I tell you do so 
* Follow common patter on how to Insert items
```kotlin
  context
            .insertInto(
                Tables.PRODUCERS,
                Tables.PRODUCERS.NAME,
                Tables.PRODUCERS.CHANNEL_ID,
            ).values(producer.name, producer.channelId)
            .returning()
            .fetchOneInto(Producer::class.java)
            ?: throw IllegalStateException("Something went wrong creating a new Producer")
```
* Always use .fetchOneInto(Producer::class.java) to get a single result into a DTO
* Always use .fetchInto(Producer::class.java) to get multiple items into a DTO
* 
