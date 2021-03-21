# Jackstomp

[![Maven central version](https://img.shields.io/maven-central/v/org.hildan.jackstomp/jackstomp.svg)](http://mvnrepository.com/artifact/org.hildan.jackstomp/jackstomp)
[![Github Build](https://img.shields.io/github/workflow/status/joffrey-bion/jackstomp/CI%20Build?label=build&logo=github)](https://github.com/joffrey-bion/jackstomp/actions?query=workflow%3A%22CI+Build%22)
[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/joffrey-bion/jackstomp/blob/master/LICENSE)

A tiny wrapper around spring SockJS client to make it easy to use with Jackson-serialized objects on STOMP.

Jackstomp is helpful to either build a client in no time or write integration tests for your Java websocket server.

## Basic usage

Jackstomp is here to deal with boilerplate for you. The default `JackstompClient` simply uses Spring's STOMP websocket 
client with a Jackson converter. It encapsulates the creation and configuration of all the small elements to put 
together, so that you can deal with the important stuff instead.

```java
JackstompClient client = new JackstompClient();
JackstompSession session = client.connect("ws://myapp.org/websocket");

MyPojo pojoMsg = new MyPojo(); // any serializable java object

// send your object, serialized to JSON, over the websocket using STOMP
session.send("/app/messages", msg); 
```

`JackstompSession` extends `StompSession`, so you can expect all the usual methods like `send()` and `subscribe()`.

## Active subscriptions for unit tests

Since subscriptions are here for server pushes, you usually need to declare a handler class for the received messages.
However, this is not what we want for unit tests, as in this case we know which subscription we expect to receive data 
on, and thus we want to be able to actively check if something came up.

Jackstomp adds the concept of `Channel<T>`, which you create when subscribing to a STOMP destination. Just specify the 
type you expect to receive on the websocket as JSON, and Jackstomp will deal with the handler, the queuing etc. You get
a `Channel<T>` for your type `T`, which you can then query for data:

```java
JackstompClient client = new JackstompClient();
JackstompSession session = client.connect("ws://myapp.org/websocket");

MyPojo pojoMsg = new MyPojo(); // any serializable java object

// send your object, serialized to JSON, over the websocket using STOMP
Channel<MyPojo> messages = session.subscribe("/topic/messages", MyPojo.class);

MyPojo msg = messages.next(); // blocks until a msg is received, returns null after a default timeout
assertNotNull(msg);
assertEquals(myExpectedPojo, msg);
```

## Request/response pattern

Sometimes, what you need is a basic request/response, but still on websockets. `JackstompSession.request()` implements
 this pattern using a subscription and a SEND, expecting a response on the subscribed destination:
 
```java
User newUser = new User("Bob");

User createdUser = session.request(newUser, User.class, "/app/createUser", "/topic/createdUsers");

assertNotNull(createdUser);
assertEquals(newUser, createdUser);
```

Which is equivalent to:

```java
User newUser = new User("Bob");

Channel<User> createdUsers = session.subscribe("/topic/createdUsers", User.class);
session.send("/app/createUser", newUser);
User createdUser = createdUsers.next();
createdUsers.unsubscribe();

assertNotNull(createdUser);
assertEquals(newUser, createdUser);
```
