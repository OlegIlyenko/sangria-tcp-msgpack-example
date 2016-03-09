## Sangria TCP/MessagePack Example

An example TCP GraphQL server that uses a binary data format ([MessagePack](http://msgpack.org/)).

After starting the server with

    sbt run
    
If you would like to experiment and change code yourself, then better alternative would be an [sbt-revolver](https://github.com/spray/sbt-revolver) plugin which is already available in the project. You just need yo run `sbt ~reStart` and it will automatically compile and restart the server on every change.

## Motivation