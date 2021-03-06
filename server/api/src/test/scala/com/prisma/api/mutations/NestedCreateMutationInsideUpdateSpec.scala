package com.prisma.api.mutations

import com.prisma.api.ApiBaseSpec
import com.prisma.api.database.DatabaseQueryBuilder
import com.prisma.shared.project_dsl.SchemaDsl
import org.scalatest.{FlatSpec, Matchers}

class NestedCreateMutationInsideUpdateSpec extends FlatSpec with Matchers with ApiBaseSpec {

  "a P1! to C1! relation" should "error since old required parent relation would be broken" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToOneRelation_!("parentReq", "childReq", parent)
    }
    database.setup(project)

    val res = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childReq: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    id
          |    childReq{
          |       id
          |    }
          |  }
          |}""".stripMargin,
        project
      )

    val parentId = res.pathAsString("data.createParent.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  updateParent(
         |  where: {id: "$parentId"}
         |  data:{
         |    p: "p2"
         |    childReq: {create: {c: "SomeC"}}
         |  }){
         |  p
         |  childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate the required relation '_ChildToParent' between Child and Parent"
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a P1! to C1 relation" should "work" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation_!("childReq", "parentOpt", child, isRequiredOnFieldB = false)
    }
    database.setup(project)

    val res = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childReq: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |  id
          |    childReq{
          |       id
          |    }
          |  }
          |}""".stripMargin,
        project
      )

    val parentId = res.pathAsString("data.createParent.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

    val res2 = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {id: "$parentId"}
         |  data:{
         |    p: "p2"
         |    childReq: {create: {c: "SomeC"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res2.toString should be("""{"data":{"updateParent":{"childReq":{"c":"SomeC"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1 to C1  relation " should "work" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation("childOpt", "parentOpt", child)
    }
    database.setup(project)

    val res = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childOpt: {
          |      create: {c: "c1"}
          |    }
          |  }){
          |    id
          |    childOpt{
          |       id
          |    }
          |  }
          |}""".stripMargin,
        project
      )

    val parentId = res.pathAsString("data.createParent.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

    val res2 = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where:{id: "$parentId"}
         |  data:{
         |    p: "p2"
         |    childOpt: {create: {c: "SomeC"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res2.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"SomeC"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a P1 to C1  relation with the parent without a relation" should "work" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToOneRelation("childOpt", "parentOpt", child)
    }
    database.setup(project)

    val parent1Id = server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {p: "p1"})
          |  {
          |    id
          |  }
          |}""".stripMargin,
        project
      )
      .pathAsString("data.createParent.id")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where:{id: "$parent1Id"}
         |  data:{
         |    p: "p2"
         |    childOpt: {create: {c: "SomeC"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"SomeC"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))
  }

  "a PM to C1!  relation with a child already in a relation" should "work" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToManyRelation_!("childrenOpt", "parentReq", child)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childrenOpt: {create: {c: "c2"}}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"}]}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(2))
  }

  "a P1 to C1!  relation with the parent and a child already in a relation" should "error in a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      schema.model("Child").field_!("c", _.String, isUnique = true).oneToOneRelation_!("parentReq", "childOpt", parent, isRequiredOnFieldB = false)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    server.executeQuerySimpleThatMustFail(
      s"""
         |mutation {
         |  updateParent(
         |  where: {p: "p1"}
         |  data:{
         |    childOpt: {create: {c: "c2"}}
         |  }){
         |    childOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project,
      errorCode = 3042,
      errorContains = "The change you are trying to make would violate the required relation '_ChildToParent' between Child and Parent"
    )
  }

  "a P1 to C1!  relation with the parent not already in a relation" should "work in a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      schema.model("Child").field_!("c", _.String, isUnique = true).oneToOneRelation_!("parentReq", "childOpt", parent, isRequiredOnFieldB = false)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
          |  createParent(data: {
          |    p: "p1"
          |
          |  }){
          |    p
          |  }
          |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(0))

    val res = server.executeQuerySimple(
      s"""
           |mutation {
           |  updateParent(
           |  where: {p: "p1"}
           |  data:{
           |    childOpt: {create: {c: "c1"}}
           |  }){
           |    childOpt {
           |      c
           |    }
           |  }
           |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c1"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a PM to C1  relation with the parent already in a relation" should "work through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val child = schema.model("Child").field_!("c", _.String, isUnique = true)
      schema.model("Parent").field_!("p", _.String, isUnique = true).oneToManyRelation("childrenOpt", "parentOpt", child)
    }
    database.setup(project)

    server
      .executeQuerySimple(
        """mutation {
          |  createParent(data: {
          |    p: "p1"
          |    childrenOpt: {
          |      create: [{c: "c1"}, {c: "c2"}]
          |    }
          |  }){
          |    childrenOpt{
          |       c
          |    }
          |  }
          |}""".stripMargin,
        project
      )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(2))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: { p: "p1"}
         |  data:{
         |    childrenOpt: {create: [{c: "c3"}]}
         |  }){
         |    childrenOpt {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"},{"c":"c3"}]}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ParentToChild").as[Int]) should be(Vector(3))
  }

  "a P1! to CM  relation with the parent already in a relation" should "work through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation_!("parentsOpt", "childReq", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childReq: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childReq{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: {p: "p1"}
         |  data:{
         |    childReq: {create: {c: "c2"}}
         |  }){
         |    childReq {
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childReq":{"c":"c2"}}}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a P1 to CM  relation with the child already in a relation" should "work through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).oneToManyRelation("parentsOpt", "childOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childOpt: {
        |      create: {c: "c1"}
        |    }
        |  }){
        |    childOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |    where: {p: "p1"}
         |    data:{
         |    childOpt: {create: {c: "c2"}}
         |  }){
         |    childOpt{
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childOpt":{"c":"c2"}}}}""")

    server.executeQuerySimple(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"c":"c1","parentsOpt":[]},{"c":"c2","parentsOpt":[{"p":"p1"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(1))
  }

  "a PM to CM  relation with the children already in a relation" should "be disconnectable through a nested mutation by unique" in {
    val project = SchemaDsl() { schema =>
      val parent = schema.model("Parent").field_!("p", _.String, isUnique = true)
      val child  = schema.model("Child").field_!("c", _.String, isUnique = true).manyToManyRelation("parentsOpt", "childrenOpt", parent)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createParent(data: {
        |    p: "p1"
        |    childrenOpt: {
        |      create: [{c: "c1"},{c: "c2"}]
        |    }
        |  }){
        |    childrenOpt{
        |       c
        |    }
        |  }
        |}""".stripMargin,
      project
    )

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(2))

    val res = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateParent(
         |  where: { p: "p1"}
         |  data:{
         |    childrenOpt: {create: [{c: "c3"}]}
         |  }){
         |    childrenOpt{
         |      c
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )

    res.toString should be("""{"data":{"updateParent":{"childrenOpt":[{"c":"c1"},{"c":"c2"},{"c":"c3"}]}}}""")

    server.executeQuerySimple(s"""query{children{c, parentsOpt{p}}}""", project).toString should be(
      """{"data":{"children":[{"c":"c1","parentsOpt":[{"p":"p1"}]},{"c":"c2","parentsOpt":[{"p":"p1"}]},{"c":"c3","parentsOpt":[{"p":"p1"}]}]}}""")

    database.runDbActionOnClientDb(DatabaseQueryBuilder.itemCountForTable(project.id, "_ChildToParent").as[Int]) should be(Vector(3))
  }

  "a one to many relation" should "be creatable through a nested mutation" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field("text", _.String)
      schema.model("Todo").oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val createResult = server.executeQuerySimple(
      """mutation {
        |  createTodo(data:{}){
        |    id
        |  }
        |}
      """.stripMargin,
      project
    )
    val id = createResult.pathAsString("data.createTodo.id")

    val result = server.executeQuerySimple(
      s"""mutation {
        |  updateTodo(
        |    where: {
        |      id: "$id"
        |    }
        |    data:{
        |      comments: {
        |        create: [{text: "comment1"}, {text: "comment2"}]
        |      }
        |    }
        |  ){
        |    comments {
        |      text
        |    }
        |  }
        |}
      """.stripMargin,
      project
    )

    mustBeEqual(result.pathAsJsValue("data.updateTodo.comments").toString, """[{"text":"comment1"},{"text":"comment2"}]""")
  }

  "a many to one relation" should "be creatable through a nested mutation" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field("text", _.String)
      schema.model("Todo").field_!("title", _.String).oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    val createResult = server.executeQuerySimple(
      """mutation {
        |  createComment(data:{}){
        |    id
        |  }
        |}
      """.stripMargin,
      project
    )
    val id = createResult.pathAsString("data.createComment.id")

    val result = server.executeQuerySimple(
      s"""
        |mutation {
        |  updateComment(
        |    where: {
        |      id: "$id"
        |    }
        |    data: {
        |      todo: {
        |        create: {title: "todo1"}
        |      }
        |    }
        |  ){
        |    id
        |    todo {
        |      title
        |    }
        |  }
        |}
      """.stripMargin,
      project
    )
    mustBeEqual(result.pathAsString("data.updateComment.todo.title"), "todo1")
  }

  "a many to one relation" should "be creatable through a nested mutation using non-id unique field" in {
    val project = SchemaDsl() { schema =>
      val comment = schema.model("Comment").field("text", _.String, isUnique = true)
      schema.model("Todo").field_!("title", _.String, isUnique = true).oneToManyRelation("comments", "todo", comment)
    }
    database.setup(project)

    server.executeQuerySimple(
      """mutation {
        |  createComment(data:{ text: "comment|"}){
        |    id
        |    text
        |  }
        |}
      """.stripMargin,
      project
    )

    val result = server.executeQuerySimple(
      s"""
         |mutation {
         |  updateComment(
         |    where: {
         |      text: "comment|"
         |    }
         |    data: {
         |      todo: {
         |        create: {title: "todo1"}
         |      }
         |    }
         |  ){
         |    id
         |    todo {
         |      title
         |    }
         |  }
         |}
      """.stripMargin,
      project
    )
    mustBeEqual(result.pathAsString("data.updateComment.todo.title"), "todo1")
  }

}
