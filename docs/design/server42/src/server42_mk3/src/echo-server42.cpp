#include <vector>
#include <unistd.h>
#include <sstream>

#include <pcapplusplus/SystemUtils.h>
#include <pcapplusplus/DpdkDeviceList.h>
#include <pcapplusplus/TablePrinter.h>

#define BOOST_STACKTRACE_LINK
#define BOOST_STACKTRACE_USE_BACKTRACE

#include <boost/stacktrace.hpp>
#include <boost/filesystem.hpp>
#include <iostream>
#include <fstream>
#include <csignal>

#include "EchoThread.h"


#define MBUF_POOL_SIZE 2097152-1
#define DEVICE_ID_1 0

#define COLLECT_STATS_EVERY_SEC 2


// Keep running flag
bool keepRunning = true;
void onApplicationInterrupted(void* cookie)
{
    keepRunning = false;
    printf("\nShutting down...\n");
}

void segv_handler(int signum) {
    ::signal(signum, SIG_DFL);
    boost::stacktrace::safe_dump_to("./backtrace.dump");
    std::cout << std::endl << boost::stacktrace::stacktrace() << std::flush << std::endl;
    ::raise(SIGABRT);
}

void printStats(pcpp::DpdkDevice* rxDevice)
{
    pcpp::DpdkDevice::DpdkDeviceStats stats;
    rxDevice->getStatistics(stats);

    std::vector<std::string> columnNames;
    columnNames.push_back(" ");
    columnNames.push_back("Total Packets");
    columnNames.push_back("Packets/sec");
    columnNames.push_back("Bytes");
    columnNames.push_back("Bits/sec");

    std::vector<int> columnLengths;
    columnLengths.push_back(10);
    columnLengths.push_back(15);
    columnLengths.push_back(15);
    columnLengths.push_back(15);
    columnLengths.push_back(15);

    pcpp::TablePrinter printer(columnNames, columnLengths);

    std::stringstream totalRx;
    totalRx << "rx" << "|" << stats.aggregatedRxStats.packets << "|" << stats.aggregatedRxStats.packetsPerSec << "|" << stats.aggregatedRxStats.bytes << "|" << stats.aggregatedRxStats.bytesPerSec*8;
    printer.printRow(totalRx.str(), '|');

    std::stringstream totalTx;
    totalTx << "tx" << "|" << stats.aggregatedTxStats.packets << "|" << stats.aggregatedTxStats.packetsPerSec << "|" << stats.aggregatedTxStats.bytes << "|" << stats.aggregatedTxStats.bytesPerSec*8;
    printer.printRow(totalTx.str(), '|');
    printer.closeTable();

    columnNames.clear();
    columnNames.push_back(" ");
    columnNames.push_back("Packets Dropeed By HW");
    columnNames.push_back("Erroneous Packets");
    columnNames.push_back("MbufAlocFailed");

    std::vector<int> columnLengths2;
    columnLengths2.push_back(10);
    columnLengths2.push_back(15);
    columnLengths2.push_back(15);
    columnLengths2.push_back(15);

    pcpp::TablePrinter printer2(columnNames, columnLengths2);
    std::stringstream errorRx;
    errorRx << "rx" << "|" << stats.rxPacketsDropeedByHW << "|" << stats.rxErroneousPackets << "|" << stats.rxMbufAlocFailed;
    printer2.printRow(errorRx.str(), '|');
}


int main(int argc, char* argv[])
{
    ::signal(SIGSEGV, &segv_handler);
    ::signal(SIGABRT, &segv_handler);

    if (boost::filesystem::exists("./backtrace.dump")) {
        // there is a backtrace
        std::ifstream ifs("./backtrace.dump");

        boost::stacktrace::stacktrace st = boost::stacktrace::stacktrace::from_dump(ifs);
        std::cout << "Previous run crashed:\n" << st << std::endl;

        // cleaning up
        ifs.close();
        boost::filesystem::remove("./backtrace.dump");
    }


    // Register the on app close event handler
    pcpp::ApplicationEventHandler::getInstance().onApplicationInterrupted(onApplicationInterrupted, NULL);

    // Initialize DPDK
    pcpp::CoreMask coreMaskToUse = pcpp::getCoreMaskForAllMachineCores();
    pcpp::DpdkDeviceList::initDpdk(coreMaskToUse, MBUF_POOL_SIZE);

    // Find DPDK devices
    pcpp::DpdkDevice* device1 = pcpp::DpdkDeviceList::getInstance().getDeviceByPort(DEVICE_ID_1);
    if (device1 == NULL)
    {
        printf("Cannot find device1 with port '%d'\n", DEVICE_ID_1);
        return 1;
    }

    pcpp::DpdkDevice::DpdkDeviceConfiguration config = pcpp::DpdkDevice::DpdkDeviceConfiguration(
            /*receiveDescriptorsNumber*/ 2048,
            /*transmitDescriptorsNumber*/ 2048,
            /*flushTxBufferTimeout*/ 100,
            /*rssHashFunction*/ 0);

    // Open DPDK devices
    if (!device1->openMultiQueues(1, 1, config))
    {
        printf("Couldn't open device1 #%d, PMD '%s'\n", device1->getDeviceId(), device1->getPMDName().c_str());
        return 1;
    }

    printf("device1 #%d, PMD '%s'\n", device1->getDeviceId(), device1->getPMDName().c_str());

    // Create worker threads
    std::vector<pcpp::DpdkWorkerThread*> workers;
    workers.push_back(new EchoThread(device1, 0));

    int workersCoreMask = 0;
    for (int i = 1; i <= 1; i++) {
        workersCoreMask = workersCoreMask | (1 << (i + 1));
    }

    printf("workersCoreMask %d", workersCoreMask);

    // Start capture in async mode
    if (!pcpp::DpdkDeviceList::getInstance().startDpdkWorkerThreads(workersCoreMask, workers))
    {
        printf("Couldn't start worker threads");
        return 1;
    }

    uint64_t counter = 0;
    int statsCounter = 1;

    // Keep running while flag is on
    while (keepRunning)
    {
        // Sleep for 1 second
        sleep(1);

        // Print stats every COLLECT_STATS_EVERY_SEC seconds
        if (counter % COLLECT_STATS_EVERY_SEC == 0)
        {
            // Clear screen and move to top left
            const char clr[] = { 27, '[', '2', 'J', '\0' };
            const char topLeft[] = { 27, '[', '1', ';', '1', 'H','\0' };
            printf("%s%s", clr, topLeft);

            printf("device1 #%d, PMD '%s' MAC:'%s' \n", device1->getDeviceId(),
                    device1->getPMDName().c_str(),
                    device1->getMacAddress().toString().c_str());

            printf("\n\nStats #%d\n", statsCounter++);
            printf("==========\n\n");

            // Print stats of traffic going from Device1 to Device2
            printf("\nDevice1 stats:\n\n");
            printStats(device1);
        }
        counter++;
    }

    // Stop worker threads
    pcpp::DpdkDeviceList::getInstance().stopDpdkWorkerThreads();

    // Exit app with normal exit code
    return 0;
}
