# Assignment Solution

## Instruction
### Installing Kotlin via Homebrew

```bash
$ brew update
$ brew install kotlin
```

Not a fan of Homebrew? See more options for installing Kotlin check out the [official tutorial](https://kotlinlang.org/docs/tutorials/command-line.html) 

### Checkout and Run
```
$ git clone https://github.com/charlesmarvin/atas.git
$ cd atas
$ kotlinc -script solution.kts large
```

To run the solution on a different input set replace `large` with the name of the directory containing your input data. The following files are expected:
- `banks.csv`
-	`covenants.csv`
- `facilities.csv`	
- `loans.csv`

Running the solution produces 2 files in the current directory: `assignments.csv` and `yields.csv`. Running the solution multiple times will override the last output file.

## Writeup

1. How long did you spend working on the problem? What did you find to be the most difficult part?
* I spent ~6 hrs in total. A correct first stab with no I/O was done in about 90mins. That said, I had a few challenges:
  * I chose to do the problem in Kotlin. I dont use Kotlin everyday but I really liked the language when I came across it a few months ago.
  * I initially used IntelliJ to create the maven Kotlin project but then decided that my reviewers should have to install maven to run my solution so I refactored to make my solution a kotlin script.
  * I first implemented the input file parsing using jackson to read the CSV to my DTO classes but I decided to remove all dependencies from my script
2. How would you modify your data model or code to account for an eventual introduction of new, as-of-yet unknown types of covenants, beyond just maximum default likelihood and state restrictions?
```
data class CovenantCondition(
  name: String
  value: Any?
)
data class Covenant(
    val bankId: Int,
    val facilityId: Int?,
    val conditions: List<CovenantCondition>
)
```
3. How would you architect your solution as a production service wherein new facilities can be introduced at arbitrary points in time. Assume these facilities become available by the finance team emailing your team and describing the addition with a new set of CSVs.
* I would architect an event driven system where we would create an email account for the finance team to email the new facilities attachments. We would write a job to poll the email server for new emails. When new mails are received that contains attachments we would raise an event. Given that the files may be large we could store the file (e.g. persist to S3) then raise an event with file url. The consumer of the event (perhaps a lambda function) would process the file and post them to the facility service where they would become available immediately for funding. Success and errors can raise events which would kick off emails to the interested parties
4. Your solution most likely simulates the streaming process by directly calling a method in your code to process the loans inside of a for loop. What would a REST API look like for this same service? Stakeholders using the API will need, at a minimum, to be able to request a loan be assigned to a facility, and read the funding status of a loan, as well as query the capacities remaining in facilities.
* `POST /solution/facilities` - creates a new facility

  `POST /solution/facilities/{id}/loans` - assigns a loan to a facility
  
  `GET /solution/loans/{id}?fields=fundingStatus` - gets attributes of a loan

  `GET /solution/facilities/{id}?fields=availableAmount` - gets attribute of a facility
* Creating endpoints for every attribute is less than ideal. Adding field filter query param here is like a poor coder's GraphQL. 
5. How might you improve your assignment algorithm if you were permitted to assign loans in batch rather than streaming? We are not looking for code here, but pseudo code or description of a revised algorithm appreciated.
* For a given facility you could loop over the batch of unfunded loans removing them once funded while the facility still has capacity
6. Discuss your solution’s runtime complexity.
* I loop over every facility (in the worst case) for every loan so O(n*m) for loan funding. This could in theory be improved by keeping a different list for facilities where we remove fully drawn facilities but I felt that optimization was not worth the complexity. I break fast on iteration so best case would be pretty good.
* I sort the facilities by interest rate once on input so thats typically O(n log n)
* All inputs are iterated over once at the start. All other operations are map lookups 