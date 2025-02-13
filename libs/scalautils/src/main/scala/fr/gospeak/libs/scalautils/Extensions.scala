package fr.gospeak.libs.scalautils

import java.time.Instant
import java.util.Optional

import cats.MonadError
import cats.data.NonEmptyList
import cats.effect.IO
import fr.gospeak.libs.scalautils.domain.MultiException

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object Extensions {

  implicit class BooleanExtension(val in: Boolean) extends AnyVal {
    def reverse: Boolean = !in

    def toOption[A](a: => A): Option[A] = if (in) Some(a) else None

    def toOption: Option[Unit] = toOption(())

    def toIO[A](a: => A, e: => Throwable): IO[A] = if (in) IO.pure(a) else IO.raiseError(e)

    def toIO(e: => Throwable): IO[Unit] = toIO((), e)
  }

  implicit class StringExtension(val in: String) extends AnyVal {
    def tryInt: Try[Int] = Try(in.toInt)

    def tryLong: Try[Long] = Try(in.toLong)

    def tryFloat: Try[Float] = Try(in.toFloat)

    def tryDouble: Try[Double] = Try(in.toDouble)
  }

  implicit class TraversableOnceExtension[A, M[X] <: TraversableOnce[X]](val in: M[A]) extends AnyVal {
    // move targeted element one place before (or after) in the collection
    def swap(elt: A, before: Boolean = true)(implicit cbf: CanBuildFrom[M[A], A, M[A]]): M[A] = {
      val coll = cbf(in)
      if (in.nonEmpty) {
        val list = in.toVector
        var i = 0
        while (i < list.length) {
          if (before && i < list.length - 1 && list(i + 1) == elt) {
            coll += list(i + 1)
            coll += list(i)
            i += 2
          } else if (!before && i < list.length - 1 && list(i) == elt) {
            coll += list(i + 1)
            coll += list(i)
            i += 2
          } else {
            coll += list(i)
            i += 1
          }
        }
      }
      coll.result()
    }

    def distinctBy[B](f: A => B)(implicit cbf: CanBuildFrom[M[A], A, M[A]]): M[A] = {
      val values = mutable.Map[B, A]()
      in.foldLeft(cbf(in)) { (acc, cur) =>
        val key = f(cur)
        if (values.contains(key)) {
          acc
        } else {
          values.put(key, cur)
          acc += cur
        }
      }.result()
    }
  }

  implicit class TraversableOnceTryExtension[A, M[X] <: TraversableOnce[X]](val in: M[Try[A]]) extends AnyVal {
    def sequence(implicit cbf: CanBuildFrom[M[Try[A]], A, M[A]]): Try[M[A]] = {
      val init = Try(cbf(in) -> Seq.empty[Throwable])
      in.foldLeft(init) { (acc, cur) =>
        acc.flatMap { case (results, errors) =>
          cur.map { result => (results += result, errors) }
            .recover { case NonFatal(error) => (results, error +: errors) }
        }
      }.flatMap(sequenceResult[A, M])
    }
  }

  implicit class TraversableOnceFutureExtension[A, M[X] <: TraversableOnce[X]](val in: M[Future[A]]) extends AnyVal {
    def sequence(implicit cbf: CanBuildFrom[M[Future[A]], A, M[A]], ec: ExecutionContext): Future[M[A]] = {
      val init = Future.successful(cbf(in) -> Seq.empty[Throwable])
      in.foldLeft(init) { (acc, cur) =>
        acc.flatMap { case (results, errors) =>
          cur.map { result => (results += result, errors) }
            .recover { case NonFatal(error) => (results, error +: errors) }
        }
      }.flatMap(sequenceResult[A, M](_).toFuture)
    }
  }

  implicit class TraversableOnceIOExtension[A, M[X] <: TraversableOnce[X]](val in: M[IO[A]]) extends AnyVal {
    def sequence(implicit cbf: CanBuildFrom[M[IO[A]], A, M[A]]): IO[M[A]] = IO {
      val init = IO.pure(cbf(in) -> Seq.empty[Throwable])
      in.foldLeft(init) { (acc, cur) =>
        acc.flatMap { case (results, errors) =>
          Try(cur.unsafeRunSync())
            .map { result => (results += result, errors) }
            .recover { case NonFatal(error) => (results, error +: errors) }
            .toIO
        }
      }.flatMap(sequenceResult[A, M](_).toIO).unsafeRunSync()
    }
  }

  implicit class OptionExtension[A](val in: Option[A]) extends AnyVal {
    def toTry(e: => Throwable): Try[A] = in match {
      case Some(v) => Success(v)
      case None => Failure(e)
    }

    def toFuture(e: => Throwable): Future[A] = in match {
      case Some(v) => Future.successful(v)
      case None => Future.failed(e)
    }

    def toEither[E](e: => E): Either[E, A] = in match {
      case Some(v) => Right(v)
      case None => Left(e)
    }

    def toEither: Either[Throwable, A] = toEither(new NoSuchElementException("None.toEither"))

    def toIO(e: => Throwable): IO[A] = in match {
      case Some(v) => IO.pure(v)
      case None => IO.raiseError(e)
    }

    def swap: Option[Unit] = in match {
      case Some(_) => None
      case None => Some(())
    }
  }

  implicit class OptionTryExtension[A](val in: Option[Try[A]]) extends AnyVal {
    def sequence: Try[Option[A]] = in match {
      case Some(Success(a)) => Success(Some(a))
      case Some(Failure(e)) => Failure(e)
      case None => Success(None)
    }
  }

  implicit class OptionalExtension[A](val in: Optional[A]) extends AnyVal {
    def asScala: Option[A] = if (in.isPresent) Some(in.get()) else None
  }

  implicit class TryExtension[A](val in: Try[A]) extends AnyVal {
    def mapFailure(f: Throwable => Throwable): Try[A] =
      in.recoverWith { case NonFatal(e) => Failure(f(e)) }

    def toFuture: Future[A] = Future.fromTry(in)

    def toIO: IO[A] = in match {
      case Success(a) => IO.pure(a)
      case Failure(e) => IO.raiseError(e)
    }
  }

  implicit class FutureExtension[A](val in: Future[A]) extends AnyVal {
    def mapFailure(f: Throwable => Throwable)(implicit ec: ExecutionContext): Future[A] =
      in.recoverWith { case NonFatal(e) => Future.failed(f(e)) }

    def failWithTry(implicit ec: ExecutionContext): Future[Try[A]] =
      in.map(Success(_)).recover { case NonFatal(e) => Failure(e) }
  }

  // conflicts with some cats implicits :(
  implicit class EitherExtension[E, A](val in: Either[E, A]) extends AnyVal {
    def get: A = in match {
      case Right(v) => v
      case Left(e: Throwable) => throw e
      case Left(e) => throw new NoSuchElementException(s"Left($e).get")
    }

    def leftMap[F](f: E => F): Either[F, A] = in match {
      case Right(_) => in.asInstanceOf[Either[F, A]]
      case Left(e) => Left(f(e))
    }

    def orElse(other: => Either[E, A]): Either[E, A] = in match {
      case Right(a) => Right(a)
      case Left(e) => other match {
        case Right(a) => Right(a)
        case Left(_) => Left(e)
      }
    }

    def toIO(implicit ev: E <:< Throwable): IO[A] = in match {
      case Right(a) => IO.pure(a)
      case Left(e) => IO.raiseError(e)
    }

    def toIO(f: E => Throwable): IO[A] = in match {
      case Right(a) => IO.pure(a)
      case Left(e) => IO.raiseError(f(e))
    }
  }

  implicit class InstantExtension(val in: Instant) extends AnyVal {
    def plus(d: FiniteDuration): Instant = in.plusMillis(d.toMillis)
  }

  implicit class IOExtension[A](val in: IO[A]) extends AnyVal {
    def filter(p: A => Boolean): IO[A] =
      in.flatMap(v => if (p(v)) IO.pure(v) else IO.raiseError(new NoSuchElementException(s"Predicate does not hold for $v")))

    def mapFailure(f: Throwable => Throwable): IO[A] = {
      in.recoverWith { case NonFatal(e) => IO.raiseError(f(e)) }
    }

    def recover[B](pf: PartialFunction[Throwable, A]): IO[A] = {
      implicitly[MonadError[IO, Throwable]].recover(in)(pf)
    }

    def recoverWith[B](pf: PartialFunction[Throwable, IO[A]]): IO[A] = {
      implicitly[MonadError[IO, Throwable]].recoverWith(in)(pf)
    }
  }

  implicit class OptionIOExtension[A](val in: Option[IO[A]]) extends AnyVal {
    def sequence: IO[Option[A]] = in match {
      case Some(io) => io.map(Some(_))
      case None => IO.pure(None)
    }
  }

  private def sequenceResult[A, M[X] <: TraversableOnce[X]](in: (mutable.Builder[A, M[A]], Seq[Throwable])): Try[M[A]] = {
    val (results, errors) = in
    NonEmptyList.fromList(errors.reverse.toList)
      .map(_.flatMap {
        case MultiException(errs) => errs
        case e => NonEmptyList.of(e)
      })
      .map(errs => if (errs.length == 1) Failure(errs.head) else Failure(MultiException(errs)))
      .getOrElse(Success(results.result()))
  }

}
