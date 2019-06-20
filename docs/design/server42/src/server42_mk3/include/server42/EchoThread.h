#pragma once


#include <pcapplusplus/DpdkDevice.h>
#include <pcapplusplus/DpdkDeviceList.h>

class EchoThread : public pcpp::DpdkWorkerThread
{
 private:
	pcpp::DpdkDevice* m_Device;
	bool m_Stop;
	uint32_t m_CoreId;
    uint32_t m_queue;

public:
 	// c'tor
	EchoThread(pcpp::DpdkDevice* device, uint32_t queue);

	// d'tor (does nothing)
	~EchoThread() { }

	// implement abstract method

	// start running the worker thread
	bool run(uint32_t coreId);

	// ask the worker thread to stop
	void stop();

	// get worker thread core ID
	uint32_t getCoreId();
};
