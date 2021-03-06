<!-- translated -->
<!--
# Action composition
-->
# アクション合成

<!--
This chapter introduces several ways of defining generic action functionality.
-->
この章では、アクションの一部機能を汎用的な形で切り出して定義する方法を紹介していきます。

<!--
## Basic action composition
-->
## アクション合成の基本

<!--
Let’s start with the simple example of a logging decorator: we want to log each call to this action.
-->
アクションの呼び出しをロギングする、簡単なロギングデコーレータの例から始めてみましょう。

<!--
The first way is not to define our own Action, but just to provide a helper method building a standard Action:
-->
一つめの方法は、アクション自体を定義する代わりに、アクションを生成するためのヘルパーメソッドを提供することです。

```scala
def LoggingAction(f: Request[AnyContent] => Result): Action[AnyContent] = {
  Action { request =>
    Logger.info("Calling action")
    f(request)
  }
}
```

<!--
That you can use as:
-->
このヘルパーメソッドは次のように利用できます。

```scala
def index = LoggingAction { request =>
  Ok("Hello World")
}
```

<!--
This is simple but it works only with the default `parse.anyContent` body parser as we don't have a way to specify our own body parser. We can of course define an additional helper method:
-->
この方法はとても簡単ですが、ボディパーサーを外部から指定する方法がないため、デフォルトの `parse.anyContent` ボディパーサーしか利用できません。そこで、次のようなヘルパーメソッドも定義してみます。

```scala
def LoggingAction[A](bp: BodyParser[A])(f: Request[A] => Result): Action[A] = {
  Action(bp) { request =>
    Logger.info("Calling action")
    f(request)
  }
}
```

<!--
And then:
-->
これは次のように利用できます。

```scala
def index = LoggingAction(parse.text) { request =>
  Ok("Hello World")
}
```

<!--
## Wrapping existing actions
-->
## 既存のアクションをラップする

<!--
Another way is to define our own `LoggingAction` that would be a wrapper over another `Action`:
-->
その他に、`LoggingAction` を `Action` のラッパーとして定義する方法もあります。

```scala
case class Logging[A](action: Action[A]) extends Action[A] {
  
  def apply(request: Request[A]): Result = {
    Logger.info("Calling action")
    action(request)
  }
  
  lazy val parser = action.parser
}
```

<!--
Now you can use it to wrap any other action value:
-->
これを利用すると、他のアクション値をラップすることができます。

```scala
def index = Logging { 
  Action { 
    Ok("Hello World")
  }
}
```

<!--
Note that it will just re-use the wrapped action body parser as is, so you can of course write:
-->
この方法ではラップされたアクションのボディパーサーがそのまま再利用されるため、次のような記述が可能です。

```scala
def index = Logging { 
  Action(parse.text) { 
    Ok("Hello World")
  }
}
```

<!--
> Another way to write the same thing but without defining the `Logging` class, would be:
-->
> 次のように、`Logging` クラスを定義せずに全く同じ機能を持つラッパーを記述することもできます。
> 
> ```scala
> def Logging[A](action: Action[A]): Action[A] = {
>   Action(action.parser) { request =>
>     Logger.info("Calling action")
>     action(request)
>   }
> } 
> ```

<!--
The following example is wrapping an existing action to add session variable:
-->
以下は既存のアクションをラップしてセッション変数を追加する例です。

```scala
def addSessionVar[A](action: Action[A]) = Action(action.parser) { request =>
  action(request).withSession("foo" -> "bar")
}
``` 


<!--
## A more complicated example
-->
## さらに複雑な例

<!--
Let’s look at the more complicated but common example of an authenticated action. The main problem is that we need to pass the authenticated user to the wrapped action.
-->
次は、認証を伴うアクションという、より複雑ですが一般的な例を見てみましょう。ここでの問題は、認証されたユーザだけをラップしたアクションへ通すことです。

```scala
def Authenticated(action: User => EssentialAction): EssentialAction = {
  
  // Let's define a helper function to retrieve a User
  def getUser(request: RequestHeader): Option[User] = {
    request.session.get("user").flatMap(u => User.find(u))
  }
  
  // Now let's define the new Action
  EssentialAction { request =>
    getUser(request).map(u => action(u)(request)).getOrElse {
      Done(Unauthorized)
    }
  }
  
}
```

<!--
You can use it like this:
-->
このヘルパーメソッドは次のように利用することができます。

```scala
def index = Authenticated { user =>
  Action { request =>
    Ok("Hello " + user.name)
  }
}
```

<!--
> **Note:** There is already an `Authenticated` action in `play.api.mvc.Security.Authenticated` with a more generic implementation than this example.
-->
> **注:** `play.api.mvc.Security.Authenticated` にはここで説明した例よりもっと汎用的な `Authenticated` アクションの実装が用意されています。

<!--
In the [[previous section | ScalaBodyParsers]] we said that an `Action[A]` was a `Request[A] => Result` function but this is not entirely true. Actually the `Action[A]` trait is defined as follows:
-->
[[前回のセクション | ScalaBodyParsers]] では `Action[A]` は `Request[A] => Result` の関数だとされていましたが、完全には正しくありません。実際には `Action[A]` トレイトは以下のように定義されています。

```scala
trait EssentialAction extends (RequestHeader => Iteratee[Array[Byte], Result])

trait Action[A] extends EssentialAction {
  def parser: BodyParser[A]
  def apply(request: Request[A]): Result
  def apply(headers: RequestHeader): Iteratee[Array[Byte], Result] = …
}
```

<!--
An `EssentialAction` is a function that takes the request headers and gives an `Iteratee` that will eventually parse the request body and produce a HTTP result. `Action[A]` implements `EssentialAction` as follow: it parses the request body using its body parser, gives the built `Request[A]` object to the action code and returns the action code’s result. An `Action[A]` can still be thought of as a `Request[A] => Result` function because it has an `apply(request: Request[A]): Result` method.
-->
`EssentialAction` は、リクエストヘッダを受け取り、最終的にリクエストボディをパースして HTTP の結果を生成する `Iteratee` を返す関数です。 `Action[A]` は `EssentialAction` を次のように実装します: ボディパーサを使ってリクエストボディをパースし、生成された `Request[A]` オブジェクトをアクションのコードに渡し、アクションのコードの結果を返します。`Action[A]` は `apply(request: Request[A]): Result` メソッドを持っているため、依然として `Request[A] => Result` の関数だと考えることができます。

<!--
The `EssentialAction` trait is useful to compose actions with code that requires to read some information from the request headers before parsing the request body.
-->
`EssentialAction` トレイトは、リクエストボディをパースする前にリクエストヘッダ情報の取得を行う必要があるコードを、アクションと合成する時に有効です。

<!--
Our `Authenticated` implementation above tries to find a user id in the request session and calls the wrapped action with this user if found, otherwise it returns a `401 UNAUTHORIZED` status without even parsing the request body.
-->
上記の `Authenticated` の実装は、リクエストセッションからユーザ ID を探し、見つかった場合はラップされたアクションをこのユーザーと共に呼び出し、そうでない場合は `401 UNAUTHORIZED` ステータスをリクエストボディをパースせずに返しています。

<!--
## Another way to create the Authenticated action
-->
## 認証されたアクションの別の実装方法

<!--
Let’s see how to write the previous example without wrapping the whole action:
-->
次は、先ほどの例を、アクションを丸ごとラップせずに記述してみましょう。

```scala
def Authenticated(f: (User, Request[AnyContent]) => Result) = {
  Action { request =>
    val result = for {
      id <- request.session.get("user")
      user <- User.find(id)
    } yield f(user, request)
    result getOrElse Unauthorized
  }
}
```

<!--
To use this:
-->
これは次のように利用します。

```scala
def index = Authenticated { (user, request) =>
   Ok("Hello " + user.name)
}
```

<!--
A problem here is that you can't mark the `request` parameter as `implicit` anymore. You can solve that using currying:
-->
ここでの問題は、もう `request` という引数を `implicit` 指定することはできない、ということです。これはカリー化を使うと解決できます。

```scala
def Authenticated(f: User => Request[AnyContent] => Result) = {
  Action { request =>
    val result = for {
      id <- request.session.get("user")
      user <- User.find(id)
    } yield f(user)(request)
    result getOrElse Unauthorized
  }
}
```

<!--
Then you can do this:
-->
これは次のように利用することができます。

```scala
def index = Authenticated { user => implicit request =>
   Ok("Hello " + user.name)
}
```

<!--
Another (probably simpler) way is to define our own subclass of `Request` as `AuthenticatedRequest` (so we are merging both parameters into a single parameter):
-->
別の (たぶんより簡単な) 方法は、`Request` のサブクラス `AuthenticatedRequest` を定義することです (二つの引数をひとつにまとめる、とも言い換えられます) 。

```scala
case class AuthenticatedRequest(
  user: User, private val request: Request[AnyContent]
) extends WrappedRequest(request)

def Authenticated(f: AuthenticatedRequest => Result) = {
  Action { request =>
    val result = for {
      id <- request.session.get("user")
      user <- User.find(id)
    } yield f(AuthenticatedRequest(user, request))
    result getOrElse Unauthorized
  }
}
```

<!--
And then:
-->
これを利用すると、次のような記述ができます。

```scala
def index = Authenticated { implicit request =>
   Ok("Hello " + request.user.name)
}
```

<!--
We can of course extend this last example and make it more generic by making it possible to specify a body parser:
-->
ボディパーサーを指定できるようにすると、この実装はもっと一般的な形に拡張することができます。

```scala
case class AuthenticatedRequest[A](
  user: User, private val request: Request[A]
) extends WrappedRequest(request)

def Authenticated[A](p: BodyParser[A])(f: AuthenticatedRequest[A] => Result) = {
  Action(p) { request =>
    val result = for {
      id <- request.session.get("user")
      user <- User.find(id)
    } yield f(Authenticated(user, request))
    result getOrElse Unauthorized
  }
}

// Overloaded method to use the default body parser
import play.api.mvc.BodyParsers._
def Authenticated(f: AuthenticatedRequest[AnyContent] => Result): Action[AnyContent]  = {
  Authenticated(parse.anyContent)(f)
}
```

<!-- > Play also provides a [[global filter API | ScalaHttpFilters]], which is useful for global cross cutting concerns. -->
> Play は、アプリケーション全体に渡る横断的な関心事を扱うのに便利な [[グローバルフィルター API | ScalaHttpFilters]] も提供しています。

<!--
> **Next:** [[Content negotiation | ScalaContentNegotiation]]
-->
> **次ページ:** [[コンテンツネゴシエーション | ScalaContentNegotiation]]