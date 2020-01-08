package vcfotographer

import scala.io.Source

import vcfotographer.BedSignalToolBox._

object BasicToolBox {

  def iteratorOnLineFromFile(fileName: String) = {
    io.Source.fromFile(fileName).getLines
  }

  /*
    One must be cautious of memoization; you can very quickly eat up large
    amounts of memory if you're not careful. The reason for this is that the
    memoization of the LazyList creates a structure much like
    scala.collection.immutable.List. So long as something is holding on to the
    head, the head holds on to the tail, and so it continues recursively. If, on
    the other hand, there is nothing holding on to the head (e.g. we used def to
    define the LazyList) then once it is no longer being used directly, it
    disappears.
    Note that some operations, including drop, dropWhile, flatMap or
    collect may process a large number of intermediate elements before returning.
    These necessarily hold onto the head, since they are methods on LazyList, and
    a lazy list holds its own head. For computations of this sort where
    memoization is not desired, use Iterator when possible.
    > https://www.scala-lang.org/api/current/scala/collection/immutable/LazyList.html
    */
  def lazyListFromFile(fileName: String) = {
    def fun(it: Iterator[String]): LazyList[String] = {
      it.nextOption match {
        case Some(line) => line #:: fun(it)
        case None => LazyList.empty
      }
    }

    fun(io.Source.fromFile(fileName).getLines)
  }

  case class Position(chr: String, pos: Long)
  case class PositionValue(pos: Position, value: Double)

  /** This function creates a LazyList of elements of type C resulting from type B elements
   *  (resultor argument).
   *  The type B elements are extracted (extractor argument) from a type A iterator if non
   *  existant or if a predicate (pred argument) on the computed next B (next argument) type
   *  element returns false */
  def streamOfSubStreamsFromOtherSream[A, B, C](it: Iterator[A], pred: (B) => Boolean, extractor: (A) => B, next: (B) => B, resultor: (B) => C) = {
    def fun(it: Iterator[A], b: Option[B]): LazyList[C] = {
      def returnLazyList(it: Iterator[A], b: B) = {
        resultor(b) #:: fun(it, Some(next(b)))
      }
      def returnLazyListWithNewEntry(it: Iterator[A]): LazyList[C] = {
        it.nextOption match {
          case None => LazyList.empty
          case Some(line) => {
            returnLazyList(it, extractor(line))
          }
        }
      }

      b match {
        case None => returnLazyListWithNewEntry(it)
        case Some(b) => {
          if (pred(b)) {
            returnLazyList(it, b)
          }
          else {
            returnLazyListWithNewEntry(it)
          }
        }
      }
    }
    fun(it, None)
  }

  val pred = (a: (Position, BedEntry[Double])) => (a._1.chr == a._2.span.chr) && (a._1.pos < a._2.span.stop)

  val extractor = (line: String) => {
    val entry = doubleBedEntryFromLine(line)
    val position = Position(entry.span.chr, entry.span.start)
    (position, entry)
  }

  val next = (a: (Position, BedEntry[Double])) => (Position(a._1.chr, a._1.pos + 1), a._2)

  val resultor = (a: (Position, BedEntry[Double])) => PositionValue(a._1, a._2.value)

  val testFunction2 = (it: Iterator[String]) => streamOfSubStreamsFromOtherSream(it, pred, extractor, next, resultor)

  def crazy[A, B, C](it: Iterator[A], pred: (B) => Boolean, extractor: (Iterator[A], Option[B]) => Option[B], next: (B) => B, resultor: (B) => C) = {
    def fun(it: Iterator[A], b: Option[B]): LazyList[C] = {
      def returnLazyList(it: Iterator[A], b: Option[B]) = {
        b match {
          case None => LazyList.empty
          case Some(b) => resultor(b) #:: fun(it, Some(next(b)))
        }
      }

      b match {
        case Some(b) if (pred(b)) => returnLazyList(it, Some(b))
        case Some(b) => returnLazyList(it, extractor(it, Some(b)))
        case _ => returnLazyList(it, extractor(it, None))
      }
    }
    fun(it, None)
  }

  case class BtypeEasy(pos: Position, entry: Option[BedEntry[Double]])
  case class Btype(pos: Position, entry: Option[BedEntry[Double]], nextEntry: Option[BedEntry[Double]])

  val pred3 = (b: BtypeEasy) => {
    b match {
      case BtypeEasy(pos, Some(entry)) => (pos.chr == entry.span.chr) && (pos.pos < entry.span.stop)
      case _ => false
    }
  }

  val next3 = (b: BtypeEasy) => BtypeEasy(Position(b.pos.chr, b.pos.pos + 1), b.entry)

  val resultor3 = (b: BtypeEasy) => {
    b match {
      case BtypeEasy(pos, Some(entry)) => PositionValue(b.pos, entry.value)
      case _ => PositionValue(b.pos, 0.0) // should not happen
    }
  }

  val extractor3 = (it: Iterator[String], _ :Option[BtypeEasy]) => {
    it.nextOption match {
      case None => None
      case Some(line) => {
        val entry = doubleBedEntryFromLine(line)
        val position = Position(entry.span.chr, entry.span.start)
        Some(BtypeEasy(position, Some(entry)))
      }
    }
  }

  val testFunction3 = (it: Iterator[String]) => crazy(it, pred3, extractor3, next3, resultor3)

  def testFunction(it: Iterator[String]) = {
    // Internal Recursion function
    def testFunction0(it: Iterator[String], pos: Option[Position], entry: Option[BedEntry[Double]]): LazyList[PositionValue] = {

      // Helper functions

      // Does not consume "it"
      def returnLazyList(position: Position, entry: BedEntry[Double], it: Iterator[String]): LazyList[PositionValue] = {
        PositionValue(position, entry.value) #:: testFunction0(it, Some(Position(position.chr, position.pos + 1)), Some(entry))
      }
      // Does consume "it"
      def returnLazyListWithNewEntry(it: Iterator[String]): LazyList[PositionValue] = {
        it.nextOption match {
          case None => LazyList.empty
          case Some(line) => {
            val entry = doubleBedEntryFromLine(line)
            val position = Position(entry.span.chr, entry.span.start)
            returnLazyList(position, entry, it)
          }
        }
      }

      // Actual behavior
      (pos, entry) match {
        // If position and entry are defined
        case (Some(position), Some(entry)) => {
          // If the current position is in the current entry return it with the value
          if ((position.chr == entry.span.chr) && (position.pos < entry.span.stop)) {
            returnLazyList(position, entry, it)
          }
          // Else get the next position and value from the iterator
          else {
            returnLazyListWithNewEntry(it)
          }
        }
        // In any other cases return the next position and value from the iterator
        case (_, _) => {
          returnLazyListWithNewEntry(it)
        }
      }
    }

    // Call to internal function
    testFunction0(it, None, None)
  }

  def filterAndExtract(it: Iterator[String]) = {
    testFunction(it filterNot {_.startsWith("#")})
  }

  def seekPosition(pos: Position, it: Iterator[String]) = {
    it.dropWhile(s => {
      val entry = doubleBedEntryFromLine(s)
      (entry.span.chr != pos.chr) || !((entry.span.start <= pos.pos) && (pos.pos < entry.span.stop))
    })
  }

  def lazyListFromFileWithFun(fileName: String, fun: (Iterator[String]) => LazyList[Any]) = {
    fun(iteratorOnLineFromFile(fileName))
  }
}