package edu.umass.cs.iesl.watr
package corpora
package database


import doobie.imports._
import shapeless._

// class Smokescreen extends FlatSpec with Matchers with DoobiePredef {
class Smokescreen extends DatabaseTest with DoobiePredef {

  val create: Update0 = sql"""
    CREATE TABLE person (
      id   SERIAL,
      name VARCHAR NOT NULL UNIQUE,
      age  SMALLINT,
      rank INT NOT NULL
    );
    """.update


  override def createEmptyDocumentZoningApi(): DocumentZoningApi = {
    reflowDB.docStore
  }

  override def beforeEach(): Unit = {
    reflowDB.reinit()
    reflowDB.runqOnce {
      reflowDB.veryUnsafeDropDatabase().run
    }
    reflowDB.runqOnce(create.run)
    reflowDB.runqOnce(
      defineOrderingTriggers(
        fr0"person",
        fr0"age"
      )
    )
  }
  override def afterEach(): Unit = {
    reflowDB.shutdown()
  }

  def freshTables() = {
    // veryUnsafeDropDatabase().run.transact(xa).unsafePerformSync
  }

  def appendPerson(name: String, age: Int): Unit = {
    val query = for {
      x <- sql""" insert into person (name, age) values($name, $age) """.update.run
    } yield x

    reflowDB.runq{
      query
    }
  }
  def insertPersonAt(name: String, age: Int, rank: Int): Unit = {
    val query = for {
      x <- sql""" insert into person (name, age, rank) values($name, $age, $rank) """.update.run
    } yield x

    reflowDB.runq{
      query
    }
  }
  def prependPerson(name: String, age: Int): Unit = {
    val query = for {
      x <- sql""" insert into person (name, age, rank) values($name, $age, 0) """.update.run
    } yield x

    reflowDB.runq{
      query
    }
  }

  def removePerson(name: String, age: Int): Unit = {
    val query = for {
      x <- sql"delete from person where name=$name AND age=$age".update.run
    } yield x

    reflowDB.runq{
      query
    }
  }

  // FIXME: map(_.map(...)) => traverse
  def getAll(): Seq[(String, Int, Int)] = {
    reflowDB.runq{
      sql"""select name, age, rank from person order by age,rank ASC"""
        .query[String :: Int :: Int :: HNil]
        .list
        .map{ _.map{
          case a :: b :: c :: HNil => (a, b, c)
        } }
    }
  }

  behavior of "ordering data"


  it should "maintain ordering" in {
    freshTables()

    appendPerson("oliver1", 20)
    prependPerson("oliver01", 20)
    appendPerson("oliver2", 20)
    //
    prependPerson("oliver02", 20)
    appendPerson("oliver3", 20)
    appendPerson("oliver4", 20)
    insertPersonAt("oliver4-ins", 20, 3)

    println(getAll().mkString("{\n  ", "\n  ", "\n}"))

    removePerson("oliver1", 20)
    removePerson("oliver01", 20)
    removePerson("oliver4", 20)
    println(getAll().mkString("{\n  ", "\n  ", "\n}"))
    insertPersonAt("oliver09-ins", 20, 2)
    println(getAll().mkString("{\n  ", "\n  ", "\n}"))
    removePerson("oliver09-ins", 20)
    println(getAll().mkString("{\n  ", "\n  ", "\n}"))

    appendPerson("morgan1", 3)
    prependPerson("morgan01", 3)
    appendPerson("morgan2", 3)

    println(getAll().mkString("{\n  ", "\n  ", "\n}"))

    removePerson("morgan2", 3)

    println(getAll().mkString("{\n  ", "\n  ", "\n}"))

  }


}
