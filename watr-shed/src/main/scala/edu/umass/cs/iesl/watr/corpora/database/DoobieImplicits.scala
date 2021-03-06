package edu.umass.cs.iesl.watr
package corpora
package database


import doobie.imports._
import shapeless._
import geometry._
import corpora._
import TypeTags._
import scala.reflect.runtime.universe._

trait DoobieImplicits extends DoobiePredef {
  val R = RelationModel

  type Int4 = Int :: Int :: Int :: Int :: HNil

  implicit val LTBoundsMeta: Composite[LTBounds] =
    Composite[Int4].xmap({
      case l :: t :: w :: h :: HNil =>
        LTBounds.IntReps(l, t, w, h)
    },{ltb =>
      val LTBounds.IntReps(l, t, w, h) = ltb
      l :: t :: w :: h :: HNil
    })


  implicit val StrDocumentIDMeta: Meta[String @@ DocumentID] =
    Meta[String].nxmap(
      str => DocumentID(str),
      docId => docId.unwrap
    )

  private implicit def TypeTagMeta[T: TypeTag](
    f: Int => Int@@T)(
    implicit T: TypeTag[Int@@T]
  ): Meta[Int@@T] = Meta[Int].xmap(n => f(n), _.unwrap)

  private implicit def StrTypeTagMeta[T: TypeTag](
    f: String => String@@T)(
    implicit T: TypeTag[String@@T]
  ): Meta[String@@T] = Meta[String].nxmap(n => f(n), _.unwrap)

  implicit val DocumentIDMeta   : Meta[Int@@DocumentID   ] = TypeTagMeta[DocumentID   ](DocumentID   (_))
  implicit val TextReflowIDMeta : Meta[Int@@TextReflowID ] = TypeTagMeta[TextReflowID ](TextReflowID (_))
  implicit val RegionIDMeta     : Meta[Int@@RegionID     ] = TypeTagMeta[RegionID     ](RegionID     (_))
  implicit val PageIDMeta       : Meta[Int@@PageID       ] = TypeTagMeta[PageID       ](PageID       (_))
  implicit val CharIDMeta       : Meta[Int@@CharID       ] = TypeTagMeta[CharID       ](CharID       (_))
  implicit val ImageIDMeta      : Meta[Int@@ImageID      ] = TypeTagMeta[ImageID      ](ImageID      (_))
  implicit val PageNumMeta      : Meta[Int@@PageNum      ] = TypeTagMeta[PageNum      ](PageNum      (_))
  implicit val ZoneIDMeta       : Meta[Int@@ZoneID       ] = TypeTagMeta[ZoneID       ](ZoneID       (_))
  implicit val LabelIDMeta      : Meta[Int@@LabelID      ] = TypeTagMeta[LabelID      ](LabelID      (_))
  implicit val UserIDMeta       : Meta[Int@@UserID       ] = TypeTagMeta[UserID       ](UserID       (_))
  implicit val LockGroupIDMeta  : Meta[Int@@LockGroupID  ] = TypeTagMeta[LockGroupID  ](LockGroupID  (_))
  implicit val ZoneLockIDMeta   : Meta[Int@@ZoneLockID   ] = TypeTagMeta[ZoneLockID   ](ZoneLockID   (_))

  implicit val WorkflowIDMeta   : Meta[String@@WorkflowID] = StrTypeTagMeta[WorkflowID   ](WorkflowID (_))
  implicit val StatusCodeMeta   : Meta[String@@StatusCode] = StrTypeTagMeta[StatusCode   ](StatusCode (_))
  implicit val EmailAddrMeta    : Meta[String@@EmailAddr]  = StrTypeTagMeta[EmailAddr    ](EmailAddr  (_))

  // implicit def TargetRegionMeta : Meta[R.TargetRegion] = implicitly[Meta[R.TargetRegion]]
  // implicit def ZoneMeta         : Meta[R.Zone]         = implicitly[Meta[R.Zone]]
  // implicit def TextReflowMeta   : Meta[R.TextReflow]   = implicitly[Meta[R.TextReflow]]
  // implicit def LabelMeta        : Meta[R.Label]        = implicitly[Meta[R.Label]]
  // implicit def PageMeta         : Meta[R.Page]         = implicitly[Meta[R.Page]]
  // implicit def DocumentMeta     : Meta[R.Document]     = implicitly[Meta[R.Document]]
  // implicit def PersonMeta       : Meta[R.Person]       = implicitly[Meta[R.Person]]
  // implicit def ZoneLockMeta     : Meta[R.ZoneLock]     = implicitly[Meta[R.ZoneLock]]
  // implicit def WorkflowDefMeta  : Meta[R.WorkflowDef]  = implicitly[Meta[R.WorkflowDef]]

}
