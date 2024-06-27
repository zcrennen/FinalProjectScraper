import SequentialRealatorScraper.{browser, htmlListingElement}
import net.ruippeixotog.scalascraper.scraper.ContentExtractors.text
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.*
import net.ruippeixotog.scalascraper.dsl.DSL.Extract.*
import net.ruippeixotog.scalascraper.dsl.DSL.*
import net.ruippeixotog.scalascraper.dsl.DSL.Extract.*
import net.ruippeixotog.scalascraper.dsl.DSL.Parse.*
import net.ruippeixotog.scalascraper.dsl.DSL.Parse.*
import net.ruippeixotog.scalascraper.model.Document

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source

object booksToScrapeSequential extends App{
  //open the csv file of all links to books to scrape
  // Specify the path to your file

  implicit val ec: ExecutionContext = ExecutionContext.global
  val filePath = "src/main/scala/bookslinks.csv"

  // Open the file
  val bufferedSource = Source.fromFile(filePath)

  def fetchPage(url: String): Future[Document] = Future {
    try {
      val newBrowser = JsoupBrowser()
      println(s"Fetching page: $url")
      val doc = newBrowser.get(url)
      val bookTitle = doc  >> text("h1")
      println(bookTitle)
      println(s"Fetched page successfully: $url")
      doc
    } catch {
      case e: Exception =>
        println(s"Failed to fetch page: $url. Error: ${e.getMessage}")
        throw e
    }
  }



  // Get the start time
  val startTime = System.nanoTime()
  val fetchedPages = bufferedSource.getLines().map( x => fetchPage(x))
  val allPages = Future.sequence(fetchedPages)
  // Read each line
//  for (line <- bufferedSource.getLines()) {
//    // Print the line (or do any processing)
//    println(line)
//    val doc = browser.get(line)
//    val bookTitle = doc  >> text("h1")
//    println(bookTitle)
//  }
  // Get the end time
  Await.result(allPages, Duration.Inf)
  val endTime = System.nanoTime()

  // Calculate the duration
  val durationInSeconds = (endTime - startTime) / 1e9
  println(s"Time taken: $durationInSeconds seconds")
  
  bufferedSource.close() // Close the file reading
}
