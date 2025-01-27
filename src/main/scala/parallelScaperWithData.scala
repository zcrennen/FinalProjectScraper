import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Random, Success}
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL.*
import net.ruippeixotog.scalascraper.model.Document

import scala.concurrent.duration.Duration
import net.ruippeixotog.scalascraper.dsl.DSL.Extract.*

import scala.io.Source

/*
  This file scrapes the same sites as the booksToScrepSequentail file, but it scrapes the varoius
  pages in parallel. I achieved some massive speedup by scraping the 1000 books to scrape pages in
  parallel. Obviously runtimes depend on connectoins speed, but for example, I scarped the 1000 pages
  sequentially in 299 seconds and in parallel, I scraped the pages in 39 seconds.
 */

//NOTES TO SELF
/*
  If you want to scrape by a certain calss name in a div element
  you have to add a period berfore the div class name ex -> element(".sg-col-inner")
 */

object parallelScaperWithData {
  implicit val ec: ExecutionContext = ExecutionContext.global
  case class Book(title: String, price: Double, stock: Int, url: String)

  case class FinalResults(averageBookPrice: Double, maxBookPrice: Double, lowStockCount: Int, medStockCount: Int, HighStockCount: Int)

  //A random wait timer to space out requests to amazon
  def randomWait(minSeconds: Int, maxSeconds: Int): Unit = {
    val random = new Random()
    val waitTime = minSeconds + random.nextInt((maxSeconds - minSeconds) + 1)
    println(s"Waiting for $waitTime seconds...")
    Thread.sleep(waitTime * 1000)
    println("Done waiting!")
  }

//This function currently isn't in use because amazon kept blcoking me, but it was used
//for referening book prices from books.toscrape to amazon. You can seee that I also played around with
//timeouts in this function to avoid getting blocked. 
  def findBookOnAmazon(bookTitle: String): Unit = {
    //Found this helpful method to replace parts of a string: https://www.geeksforgeeks.org/scala-string-replace-method-with-example/
    val convTitleToSearch = bookTitle.replace(" ", "+")
    println("\n\n\n Found Target Book")
    println(convTitleToSearch)
    val amazonConnection = "https://www.amazon.com/s?k=" + convTitleToSearch

    try { //Try to make an amazon connection which searches a corresponding book title
        randomWait(20, 35) //Set up a random waiting range to space out connections to Amazon and prevent from getting blocked
        val amazonBrowser = JsoupBrowser()
        println(s"Fetching Amazon page: $amazonConnection") //Update console to show which page is being scraped from amazon
        val amazonDoc = amazonBrowser.get(amazonConnection)

        val firstProduct = amazonDoc >> elements(".sg-col-inner") >> text(".a-price-whole") //After connection is established, book prices need to be scraped
        val firstProductDecimal = amazonDoc >> elements(".sg-col-inner") >> text(".a-price-fraction")
        val firstProductPriceDouble = (firstProduct + firstProductDecimal).toDouble
        println("Amazon Link: " + amazonConnection + " Amazon Price: " + firstProductPriceDouble)
    } catch { //Book could not be on amazon, if that's the case we will print that out
      case e: Exception =>
        println(s"Failed to fetch page: $amazonConnection. Error: ${e.getMessage}")
    }
  }



  def fetchPage(url: String): Future[Book] = Future {
    try {
      val newBrowser = JsoupBrowser()
      //TODO: COMMENT AND UNCOMMENT AS NECESSARY
//      println(s"Fetching page: $url") //Update console to show which page is being scraped
      val doc = newBrowser.get(url) //Establish jsoup connection

      val bookTitle = doc  >> text("h1")

      //As soon as I get a title I will search amazon
      //Following code is commented out because amazon eventually blocks scraping
//      println("before here")
//      findBookOnAmazon(bookTitle);
//      println("After here")

      val bookPrice = doc >> element(".price_color")
      val bookPriceParsed = bookPrice.text
      val bookPriceDouble = bookPriceParsed.drop(1).toDouble

      val table = doc >> element("tbody") // Select the tbody element
      val rows = (table >> elements("tr") >> elements("td")).toVector // Select all rows in the tbody
      val rowIndex = 2 // For example, to get the third row (index starts at 0)

      //Printing out the table rows
//      println("Row 0 captured: " + rows(0))
//      println("Row 1 captured: " + rows(1))
//      println("Row 2 captured: " + rows(2))
//      println("Row 3 captured: " + rows(3))
//      println("Row 4 captured: " + rows(4))
//      println("Row 5 captured: " + rows(5))
//      println("Row 6 captured: " + rows(6))

      val bookStock = rows(5).text.filter(_.isDigit).toInt //Get the book stock from the table

      Book(bookTitle, bookPriceDouble, bookStock, url)

    } catch {
      case e: Exception =>
        println(s"Failed to fetch page: $url. Error: ${e.getMessage}")
        throw e
    }
  }

  def runLogic(): Unit = {
    val filePath = "src/main/scala/bookslinks.csv"
    // Open the file
    val bufferedSource = Source.fromFile(filePath)

    // Get the start time
    val startTime = System.nanoTime()


    val urls = bufferedSource.getLines().toList
    val fetchFutures = urls.map(fetchPage)

    val allPagesFuture = Future.sequence(fetchFutures)

    val maxPriceFuture = allPagesFuture.map { books =>
      books.maxBy(_.price)
    }

    // Calculate max price and total price on complete
//    allPagesFuture.onComplete {
//      case Success(books) =>
//        val maxPriceBook = books.maxBy(_.price)
//        val totalPrice = books.map(_.price).sum
//        val avgPrice = totalPrice / books.length
//        val totalStock = books.map(_.stock).sum
//        val avgStock = totalStock / books.length
//
//        val lowStock = books.filter(_.stock < 4);
//        val medStock = books.filter(x => x.stock > 4 && x.stock < 8 );
//        val highStock = books.filter(_.stock > 12);
//
//
//
//        println(s"The book with the highest price is: ${maxPriceBook.title} at $$${maxPriceBook.price}, URL: ${maxPriceBook.url}")
//        println(s"The average price of all books is: ${avgPrice}")
//        println(s"The average stock for books is: ${avgStock}")
//        println(s"There are ${lowStock} low stock titles")
//        println(s"There are ${medStock} medium stock titles")
//        println(s"There are ${highStock} high stock titles")
//
//      case Failure(ex) =>
//        println(s"Failed to fetch and process pages. Error: ${ex.getMessage}")
//    }

    val maxPriceBook = Await.result(maxPriceFuture, Duration.Inf)
    val books = Await.result(allPagesFuture, Duration.Inf)

    val maxPrice = books.maxBy(_.price)
    val minPrice = books.minBy(_.price)
    val totalPrice = books.map(_.price).sum
    val avgPrice = totalPrice / books.length
    val totalStock = books.map(_.stock).sum
    val avgStock = totalStock / books.length

    val lowStock = books.count(_.stock < 4);
    val medStock = books.count(x => x.stock > 4 && x.stock < 8);
    val highStock = books.count(_.stock > 12);

    //Print statistics and results
    println("_________________________________________________________________________________")
    println(s"The book with the highest price is: ${maxPrice.title} at $$${maxPrice.price}, URL: ${maxPrice.url}")
    println(s"The book with the lowest price is: ${minPrice.title} at $$${minPrice.price}, URL: ${minPrice.url}")
    println(s"The average price of all books is: ${avgPrice}")
    println(s"The average stock for books is: ${avgStock}")
    println(s"There are ${lowStock} low stock titles")
    println(s"There are ${medStock} medium stock titles")
    println(s"There are ${highStock} high stock titles")
    println("_________________________________________________________________________________")

    // Get the end time
    val endTime = System.nanoTime()

    // Calculate the duration
    val durationInSeconds = (endTime - startTime) / 1e9
    println(s"Time taken: $durationInSeconds seconds")

    bufferedSource.close() // Close the file reading
  }

  def main(args: Array[String]): Unit = {
    runLogic();
  }
}
