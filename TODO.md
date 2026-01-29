1. Setup set for most common (no message) queries, channels and producers, follow plan in cache-plan.md
    * Find if there is another data structure that could help to avoid GB

2. Think how could messages keep in memory as well
    * Use pg advisory locks to still block the routing
    * Find ways to keep everything in memory, but do not lose any message
    * How can we make it compact (compression) without waisting too much time doing at runtime
    * FiFo strict ordering MUST be preserved

3. For streaming, keep track of the last record provided by the logical replication, when started, follow from the last
   message

4. Use internal steam to replicate over channels and connections to get more things in memory and speed things up