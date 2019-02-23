package io.mc.bank

import java.io.File
import kotlin.math.roundToInt

val SEPARATOR = ","
val DEFAULT_INPUT_DIR = "."

// Data
class Facility(
    val bankId: Int,
    val facilityId: Int,
    val interestRate: Float,
    val amount: Int
) {
    var availableAmount = amount
    fun drawDown(drawDownAmount: Int): Boolean {
        if (canFund(drawDownAmount)) {
            availableAmount -= drawDownAmount
            return true
        }
        return false
    }

    fun canFund(amount: Int): Boolean {
        return availableAmount >= amount
    }
}

data class Bank(
    val bankId: Int,
    val bankName: String
)

data class Covenant(
    val bankId: Int,
    val facilityId: Int?,
    val maxDefaultLikelihood: Float?,
    val bannedState: String?
)

data class Loan(
    val id: Int,
    val amount: Int,
    val interestRate: Float,
    val defaultLikelihood: Float,
    val state: String
)

data class Assignment(
    val loanId: Int,
    val facilityId: Int
)

data class Yield(
    val facilityId: Int,
    val expectedYield: Int
)

data class FundingDetails(
    val loanId: Int,
    val facilityId: Int,
    val expectedYield: Float
)

interface ExpectedYieldStrategy {
    fun calculate(loan: Loan, facility: Facility): Float
}

class DefaultExpectedYieldStrategy : ExpectedYieldStrategy {
    override fun calculate(loan: Loan, facility: Facility): Float {
        return (1 - loan.defaultLikelihood) * loan.interestRate * loan.amount - loan.defaultLikelihood * loan.amount - facility.interestRate * loan.amount
    }
}

interface FundingReporter {
    fun reportYields(fundingDetails: List<FundingDetails>)
    fun reportAssignments(fundingDetails: List<FundingDetails>)
}

class FileBasedFundingReporter(val yieldFilename: String, val assignmentFilename: String) : FundingReporter {
    override fun reportYields(fundingDetails: List<FundingDetails>) {
        val yieldOutputFile = File(yieldFilename)
        yieldOutputFile.writeText("facility_id,expected_yield\n")
        fundingDetails.groupBy({ it.facilityId }, { it.expectedYield })
            .map { (facilityId, facilityYields) -> Pair(facilityId, facilityYields.reduce {a, b -> a + b  }.roundToInt()) }
            .forEach { yieldOutputFile.appendText("${it.first},${it.second}\n") }
    }

    override fun reportAssignments(fundingDetails: List<FundingDetails>) {
        val assignmentsOutputFile = File(assignmentFilename)
        assignmentsOutputFile.writeText("loan_id,facility_id\n")
        fundingDetails.forEach { assignmentsOutputFile.appendText("${it.loanId},${it.facilityId}\n") }
    }
}

fun fund(loan: Loan, facility: Facility, yieldCalculator: ExpectedYieldStrategy): FundingDetails {
    facility.drawDown(loan.amount)
    val expectedYield = yieldCalculator.calculate(loan, facility)
    return FundingDetails(loan.id, facility.facilityId, expectedYield)
}

fun meetsCovenants(loan: Loan, facility: Facility, covenants: Map<Int, List<Covenant>>): Boolean {
    val covenantsForBank = covenants[facility.bankId]
    val meets =
        covenantsForBank?.filter { covenant -> covenant.facilityId == null || covenant.facilityId == facility.facilityId }
            ?.all { covenant -> checkCovenant(covenant, loan) }
    return meets ?: false
}

fun checkCovenant(covenant: Covenant, loan: Loan): Boolean {
    val maxDefaultLikelihood: Float =
        if (covenant.maxDefaultLikelihood != null) covenant.maxDefaultLikelihood else Float.MAX_VALUE
    return !(loan.state == covenant.bannedState || loan.defaultLikelihood > maxDefaultLikelihood)
}

// IO Utils
fun loadFacilities(fileName: String): List<Facility> {
    return load(fileName) { s: String -> parseFacility(s) }.toMutableList()
}

fun loadBanks(fileName: String): List<Bank> {
    return load(fileName) { s: String -> parseBank(s) }
}

fun loadCovenants(fileName: String): List<Covenant> {
    return load(fileName) { s: String -> parseCovenant(s) }
}

fun loadLoans(fileName: String): List<Loan> {
    return load(fileName) { s: String -> parseLoan(s) }
}

fun <T> load(fileName: String, parser: (String) -> T): List<T> {
    val bufferedReader = File(fileName).bufferedReader()
    return bufferedReader.useLines { lines -> lines.drop(1).map { parser(it) }.toList() }
}

fun parseFacility(csv: String): Facility {
    val values = csv.split(SEPARATOR)
    return Facility(
        amount = values[0].toFloat().toInt(),
        interestRate = values[1].toFloat(),
        facilityId = values[2].toInt(),
        bankId = values[3].toInt()
    )
}

fun parseBank(csv: String): Bank {
    val values = csv.split(SEPARATOR)
    return Bank(
        bankId = values[0].toInt(),
        bankName = values[1]
    )
}

fun parseCovenant(csv: String): Covenant {
    val values = csv.split(SEPARATOR)
    return Covenant(
        facilityId = values[0].toInt(),
        maxDefaultLikelihood = values[1].toFloatOrNull(),
        bankId = values[2].toInt(),
        bannedState = values[3]

    )
}

fun parseLoan(csv: String): Loan {
    val values = csv.split(SEPARATOR)
    return Loan(
        interestRate = values[0].toFloat(),
        amount = values[1].toInt(),
        id = values[2].toInt(),
        defaultLikelihood = values[3].toFloat(),
        state = values[4]
    )
}

// Main
var inputDir = DEFAULT_INPUT_DIR
if (args.size == 1) {
    inputDir = args[0]
}

val banksById = loadBanks("${inputDir}/banks.csv").map { it.bankId to it }.toMap()
val facilities = loadFacilities("${inputDir}/facilities.csv").filter { facility -> banksById.containsKey(facility.bankId) }
    .sortedWith(compareBy { it.interestRate })
val covenantsById = loadCovenants("${inputDir}/covenants.csv").groupBy { it.bankId }
val loans = loadLoans("${inputDir}/loans.csv")
val expectedYieldStrategy = DefaultExpectedYieldStrategy()
val fundingReporter = FileBasedFundingReporter(yieldFilename = "yields.csv", assignmentFilename = "assignments.csv")

val fundingDetails = loans.map { loan ->
    //filter to only facilities that can fund the requested loan amount
    facilities.filter { facility -> facility.canFund(loan.amount) }
            //get the first facility that meets all covenants for the requested loan
        .firstOrNull { facility -> meetsCovenants(loan, facility, covenantsById) }
            // fund the loan if a facility was found and return the funding detail
        ?.let { facility -> fund(loan, facility, expectedYieldStrategy) }

}.filterNotNull()

//do reporting
fundingReporter.reportYields(fundingDetails)
fundingReporter.reportAssignments(fundingDetails)

println("Processed ${fundingDetails.size} loan assignments")