## Sangria TCP/MessagePack Example

An example TCP GraphQL server that uses a binary data format ([MessagePack](http://msgpack.org/)).

After starting the server with

    sbt run
    
If you would like to experiment and change code yourself, then better alternative would be an [sbt-revolver](https://github.com/spray/sbt-revolver) plugin which is already available in the project. You just need yo run `sbt ~reStart` and it will automatically compile and restart the server on every change.

### Motivation & Protocol

GraphQL claims to be transport and data format agnostic. At least this is what everybody says (including myself). It's time to prove this theory and implement something different from boring HTTP and JSON :)

This example uses binary protocol [MessagePack](http://msgpack.org/) and exposes GraphQL query engine via raw TCP connection. Some important points about the protocol and data the format:

* In find it important for a GraphQL library not to depend on particular data format internally. This means that even during the execution of a query, an engine should construct target data format where possible. This avoids any unnecessary intermediate representation (like JSON) which just pollutes memory and increases GC time. This was one of the key design goals for sangria. In order to achieve this sangria has 2 main low-level abstractions: `ResultMarshaller` and `InputUnmarshaller`. They both provide the knowledge about the target data format. By adding following import: 
 
  ```scala
  import sangria.marshalling.msgpack._
  ```
  
  You are bringing in the knowledge about the MessagePack data format (it imports appropriate `ResultMarshaller` and `InputUnmarshaller` instances). This allows execution engine directly construct binary data along the way, without any intermediate representation.
* The binary protocol is pretty simple: client and servers send each other MessagePack `Map` values prefixed by 4 byte integer value in little-endian format. The prefix holds a size of the data frame (query/response) that follows it. Client always initiates the interaction.
    
## Feedback

Feedback is very welcome in any form :) Feel free to make PRs, post issues or join [the chat](https://gitter.im/sangria-graphql/sangria).  