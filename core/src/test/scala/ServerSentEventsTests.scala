package lol.http

import ServerSentEvents._

import fs2.{ Task, Stream }

import scala.concurrent.{ ExecutionContext }
import ExecutionContext.Implicits.global

class ServerSentEventsTests extends Tests {

  val App: Service = {
    case url"/" =>
      Ok("Hello")
    case url"/stream" =>
      Ok(Stream(Event("Hello"), Event("World")))
    case url"/fakeStream" =>
      Ok("Hello").addHeaders(h"Content-Type" -> h"text/event-stream")
  }

  test("Valid string events stream") {
    withServer(Server.listen()(App)) { server =>
      await() {
        Client("localhost", server.port).runAndStop { client =>
          client.run(Get("/stream")) { response =>
            response.readAs[Stream[Task,Event[String]]].flatMap { eventStream =>
              eventStream.runLog.map(_.toList).unsafeRunAsyncFuture
            }
          }
        }
      } should be (List(Event("Hello"), Event("World")))
    }
  }

  test("Not an events stream") {
    withServer(Server.listen()(App)) { server =>
      the [Error] thrownBy await() {
        Client("localhost", server.port).runAndStop { client =>
          client.run(Get("/")) { response =>
            response.readAs[Stream[Task,Event[String]]].flatMap { eventStream =>
              eventStream.runLog.map(_.toList).unsafeRunAsyncFuture
            }
          }
        }
      } should be (Error.UnexpectedContentType())
    }
  }

  test("Invalid events stream ") {
    withServer(Server.listen()(App)) { server =>
      await() {
        Client("localhost", server.port).runAndStop { client =>
          client.run(Get("/fakeStream")) { response =>
            response.readAs[Stream[Task,Event[String]]].flatMap { eventStream =>
              eventStream.runLog.map(_.toList).unsafeRunAsyncFuture
            }
          }
        }
      } should be (Nil)
    }
  }

}
