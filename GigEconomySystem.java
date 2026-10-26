import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

/*
 * GIG-ECONOMY WORKER PAYMENT RECONCILIATION SYSTEM
 *
 * Module 1 -> Worker & Job Record
 * Module 2 -> Earnings & Platform Fee
 * Module 3 -> Transaction Ledger & Payout Schedule
 * Module 4 -> Payment Reconciliation
 */

// =========================
// MODULE 1 + MODULE 2
// =========================

class Job {
    private String jobId;
    private String jobTitle;
    private String platformName;
    private double agreedAmount;
    private double platformFeeRate;

    public Job(String jobId, String jobTitle,
               String platformName, double agreedAmount,
               double platformFeeRate) {

        this.jobId = jobId;
        this.jobTitle = jobTitle;
        this.platformName = platformName;
        this.agreedAmount = agreedAmount;
        this.platformFeeRate = platformFeeRate;
    }

    // Module 2
    public double getGrossAmount() {
        return agreedAmount;
    }

    public double calculatePlatformFee() {
        return agreedAmount * platformFeeRate / 100;
    }

    public double calculateNetEarning() {
        return agreedAmount - calculatePlatformFee();
    }

    public String getJobId() {
        return jobId;
    }

    public void displayJob() {

        System.out.println("Job ID        : " + jobId);
        System.out.println("Job Title     : " + jobTitle);
        System.out.println("Platform      : " + platformName);
        System.out.println("Gross Amount  : Rs. " + agreedAmount);
        System.out.println("Platform Fee  : Rs. "
                + calculatePlatformFee());
        System.out.println("Net Earning   : Rs. "
                + calculateNetEarning());
    }
}


// =========================
// WORKER CLASS
// =========================

class Worker {

    private String workerId;
    private String workerName;
    private String phoneNumber;

    private Job[] jobs;

    public Worker(String workerId,
                  String workerName,
                  String phoneNumber,
                  int jobCount) {

        this.workerId = workerId;
        this.workerName = workerName;
        this.phoneNumber = phoneNumber;

        jobs = new Job[jobCount];
    }

    public String getWorkerId() {
        return workerId;
    }

    public void addJob(int index, Job job) {
        jobs[index] = job;
    }

    public Job[] getJobs() {
        return jobs;
    }

    // Module 1 + Module 2 display
    public void displayWorker() {

        System.out.println("\n================================");
        System.out.println("       WORKER RECORD");
        System.out.println("================================");

        System.out.println("Worker ID     : " + workerId);
        System.out.println("Worker Name   : " + workerName);
        System.out.println("Phone Number  : " + phoneNumber);
        System.out.println("Number of Jobs: " + jobs.length);

        double totalGross = 0;
        double totalFee = 0;
        double totalNet = 0;

        for (int i = 0; i < jobs.length; i++) {

            System.out.println("\nJob " + (i + 1));
            System.out.println("----------------------------");

            jobs[i].displayJob();

            totalGross += jobs[i].getGrossAmount();
            totalFee += jobs[i].calculatePlatformFee();
            totalNet += jobs[i].calculateNetEarning();
        }

        System.out.println("\n================================");
        System.out.println("             TOTALS");
        System.out.println("================================");

        System.out.println("Total Gross : Rs. " + totalGross);
        System.out.println("Total Fee   : Rs. " + totalFee);
        System.out.println("Total Net   : Rs. " + totalNet);
    }
}


// =========================
// MODULE 3
// TRANSACTION CLASS
// (now with payout schedule)
// =========================

class Transaction {

    private String transactionId;
    private String workerId;
    private String jobId;
    private double amount;
    private String type;

    // Payout schedule
    private LocalDate scheduledDate;   // when payout was due
    private LocalDate paidDate;        // null if not yet paid

    public Transaction(String transactionId,
                       String workerId,
                       String jobId,
                       double amount,
                       String type,
                       LocalDate scheduledDate,
                       LocalDate paidDate) {

        this.transactionId = transactionId;
        this.workerId = workerId;
        this.jobId = jobId;
        this.amount = amount;
        this.type = type;
        this.scheduledDate = scheduledDate;
        this.paidDate = paidDate;
    }

    public String getJobId() {
        return jobId;
    }

    public double getAmount() {
        return amount;
    }

    /*
     * PENDING -> not paid yet
     * PAID    -> paid on or before the scheduled date
     * LATE    -> paid after the scheduled date
     */
    public String getPayoutStatus() {

        if (paidDate == null) {
            return "PENDING";
        }

        if (paidDate.isAfter(scheduledDate)) {
            return "LATE";
        }

        return "PAID";
    }

    private String paidDateText() {
        return (paidDate == null) ? "NOT PAID" : paidDate.toString();
    }

    public void display() {

        System.out.println(
            transactionId + " | "
            + workerId + " | "
            + jobId + " | Rs. "
            + amount + " | "
            + type + " | Due: "
            + scheduledDate + " | Paid: "
            + paidDateText() + " | "
            + getPayoutStatus()
        );
    }

    public String toFileString() {

        return transactionId + ","
                + workerId + ","
                + jobId + ","
                + amount + ","
                + type + ","
                + scheduledDate + ","
                + paidDateText() + ","
                + getPayoutStatus();
    }
}


// =========================
// MODULE 4
// RECONCILIATION CLASS
// =========================

class ReconciliationRecord {

    private String workerId;
    private String jobId;

    private double expectedPayout;
    private double actualPayout;
    private String payoutStatus;

    public ReconciliationRecord(String workerId,
                                 String jobId,
                                 double expectedPayout,
                                 double actualPayout,
                                 String payoutStatus) {

        this.workerId = workerId;
        this.jobId = jobId;
        this.expectedPayout = expectedPayout;
        this.actualPayout = actualPayout;
        this.payoutStatus = payoutStatus;
    }

    public double getDifference() {

        return actualPayout - expectedPayout;
    }

    public boolean isAmountReconciled() {

        return Math.abs(getDifference()) < 0.01;
    }

    public boolean isScheduleOk() {

        return payoutStatus.equals("PAID");
    }

    // Reconciled only if amount matches AND payout was on time
    public boolean isReconciled() {

        return isAmountReconciled() && isScheduleOk();
    }

    public void display() {

        System.out.println("Worker ID      : " + workerId);
        System.out.println("Job ID         : " + jobId);
        System.out.println("Expected Payout: Rs. "
                + expectedPayout);
        System.out.println("Actual Payout  : Rs. "
                + actualPayout);
        System.out.println("Difference     : Rs. "
                + getDifference());
        System.out.println("Payout Status  : " + payoutStatus);

        if (isReconciled()) {

            System.out.println(
                "Status         : RECONCILED"
            );

        } else {

            String reason = "";

            if (!isAmountReconciled()) {
                reason += " [AMOUNT MISMATCH]";
            }

            if (!isScheduleOk()) {
                reason += " [PAYOUT " + payoutStatus + "]";
            }

            System.out.println(
                "Status         : DISCREPANCY" + reason
            );
        }

        System.out.println();
    }
}


// =========================
// MAIN SYSTEM
// =========================

public class GigEconomySystem {

    // Reads a date in yyyy-MM-dd format.
    // If optional is true, an empty input returns null.
    private static LocalDate readDate(Scanner sc,
                                      String prompt,
                                      boolean optional) {

        while (true) {

            System.out.print(prompt);
            String input = sc.nextLine().trim();

            if (optional && input.isEmpty()) {
                return null;
            }

            try {
                return LocalDate.parse(input);
            } catch (DateTimeParseException e) {
                System.out.println(
                    "Invalid date. Use format yyyy-MM-dd."
                );
            }
        }
    }

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        System.out.println(
            "=============================================="
        );

        System.out.println(
            " GIG-ECONOMY WORKER PAYMENT RECONCILIATION"
        );

        System.out.println(
            "=============================================="
        );


        // =====================================
        // MODULE 1
        // WORKER & JOB RECORD
        // =====================================

        System.out.println("\n--------------- MODULE 1 ---------------");
        System.out.println("       WORKER & JOB RECORD");

        System.out.print("Enter Worker ID: ");
        String workerId = sc.nextLine();

        System.out.print("Enter Worker Name: ");
        String workerName = sc.nextLine();

        System.out.print("Enter Phone Number: ");
        String phoneNumber = sc.nextLine();

        System.out.print("Enter Number of Jobs: ");
        int jobCount = Integer.parseInt(sc.nextLine());

        Worker worker = new Worker(
            workerId,
            workerName,
            phoneNumber,
            jobCount
        );


        // =====================================
        // MODULE 2
        // EARNINGS & PLATFORM FEE
        // =====================================

        System.out.println("\n--------------- MODULE 2 ---------------");
        System.out.println("       EARNINGS & PLATFORM FEE");


        for (int i = 0; i < jobCount; i++) {

            System.out.println(
                "\nEnter details for Job " + (i + 1)
            );

            System.out.print("Job ID: ");
            String jobId = sc.nextLine();

            System.out.print("Job Title: ");
            String jobTitle = sc.nextLine();

            System.out.print("Platform Name: ");
            String platformName = sc.nextLine();

            System.out.print("Gross Amount: ");
            double amount =
                Double.parseDouble(sc.nextLine());

            System.out.print("Platform Fee Rate (%): ");
            double feeRate =
                Double.parseDouble(sc.nextLine());

            Job job = new Job(
                jobId,
                jobTitle,
                platformName,
                amount,
                feeRate
            );

            // Same Job object is stored
            // for all later modules.
            worker.addJob(i, job);
        }


        // Display Worker + Earnings
        worker.displayWorker();


        // =====================================
        // MODULE 3
        // TRANSACTION LEDGER & PAYOUT SCHEDULE
        // =====================================

        System.out.println("\n--------------- MODULE 3 ---------------");
        System.out.println("  TRANSACTION LEDGER & PAYOUT SCHEDULE");

        Transaction[] ledger =
            new Transaction[jobCount];

        double totalPayout = 0;


        for (int i = 0; i < jobCount; i++) {

            Job job = worker.getJobs()[i];

            System.out.println(
                "\nTransaction for Job "
                + job.getJobId()
            );

            System.out.print("Transaction ID: ");
            String transactionId = sc.nextLine();

            /*
             * Worker ID and Job ID are automatically
             * taken from the objects created earlier.
             */

            String jobId = job.getJobId();

            /*
             * Expected payout comes directly from
             * Module 2's Net Earning.
             */

            double expectedPayout =
                job.calculateNetEarning();

            System.out.println(
                "Expected Payout from Module 2: Rs. "
                + expectedPayout
            );

            // ----- Payout schedule -----

            LocalDate scheduledDate = readDate(
                sc,
                "Scheduled Payout Date (yyyy-MM-dd): ",
                false
            );

            LocalDate paidDate = readDate(
                sc,
                "Actual Paid Date (yyyy-MM-dd, "
                + "leave blank if not paid yet): ",
                true
            );

            double actualPayout = 0;

            if (paidDate != null) {

                System.out.print(
                    "Enter Actual Payout: "
                );

                actualPayout =
                    Double.parseDouble(sc.nextLine());

            } else {

                System.out.println(
                    "Payout pending. Actual payout set to Rs. 0."
                );
            }

            ledger[i] = new Transaction(
                transactionId,
                workerId,
                jobId,
                actualPayout,
                "JOB_PAYOUT",
                scheduledDate,
                paidDate
            );

            totalPayout += actualPayout;
        }


        // Display Transaction Ledger

        System.out.println(
            "\n===== TRANSACTION LEDGER ====="
        );

        for (Transaction transaction : ledger) {

            transaction.display();
        }

        System.out.println(
            "\nTotal Actual Payout: Rs. "
            + totalPayout
        );


        // =====================================
        // FILE HANDLING
        // MODULE 3
        // =====================================

        try {

            FileWriter writer =
                new FileWriter("transaction_ledger.txt");

            writer.write("TRANSACTION LEDGER\n");
            writer.write(
                "Worker ID: "
                + workerId
                + "\n"
            );

            writer.write(
                "TxnID,WorkerID,JobID,Amount,Type,"
                + "DueDate,PaidDate,Status\n"
            );

            for (Transaction transaction : ledger) {

                writer.write(
                    transaction.toFileString()
                    + "\n"
                );
            }

            writer.write(
                "Total Payout: "
                + totalPayout
                + "\n"
            );

            writer.close();

            System.out.println(
                "Ledger saved to transaction_ledger.txt"
            );

        } catch (IOException e) {

            System.out.println(
                "Error while writing ledger file."
            );
        }


        // =====================================
        // MODULE 4
        // PAYMENT RECONCILIATION
        // =====================================

        System.out.println(
            "\n--------------- MODULE 4 ---------------"
        );

        System.out.println(
            "       PAYMENT RECONCILIATION"
        );

        ReconciliationRecord[] records =
            new ReconciliationRecord[jobCount];

        int discrepancyCount = 0;


        for (int i = 0; i < jobCount; i++) {

            Job job = worker.getJobs()[i];

            double expectedPayout =
                job.calculateNetEarning();

            double actualPayout =
                ledger[i].getAmount();

            records[i] =
                new ReconciliationRecord(
                    workerId,
                    job.getJobId(),
                    expectedPayout,
                    actualPayout,
                    ledger[i].getPayoutStatus()
                );
        }


        // Reconciliation Report

        System.out.println(
            "\n===== PAYMENT RECONCILIATION REPORT ====="
        );

        for (ReconciliationRecord record :
             records) {

            record.display();

            if (!record.isReconciled()) {

                discrepancyCount++;
            }
        }


        System.out.println(
            "Total Records : "
            + jobCount
        );

        System.out.println(
            "Discrepancies Found : "
            + discrepancyCount
        );


        if (discrepancyCount == 0) {

            System.out.println(
                "All payments are reconciled."
            );

        } else {

            System.out.println(
                "Review the flagged transactions."
            );
        }


        sc.close();
    }
}
