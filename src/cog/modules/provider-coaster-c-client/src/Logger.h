/*
 * Logger.h
 *
 *  Created on: Aug 13, 2012
 *      Author: mike
 */

#ifndef LOGGER_H_
#define LOGGER_H_

#define TS_LEN 28

#include <iostream>
#include <fstream>
#include <sstream>
#include <string>

#include "coaster-defs.h"
#include "Lock.h"

namespace Coaster {

class Logger: public std::ostream {
	public:
		static Logger* instance;

		static const CoasterLogLevel NONE = COASTER_LOG_NONE;
		static const CoasterLogLevel DEBUG = COASTER_LOG_DEBUG;
		static const CoasterLogLevel INFO = COASTER_LOG_INFO;
		static const CoasterLogLevel WARN = COASTER_LOG_WARN;
		static const CoasterLogLevel ERROR = COASTER_LOG_ERROR;
		static const CoasterLogLevel FATAL = COASTER_LOG_FATAL;
	private:
		std::ostream* out;
		std::stringstream buffer;
		CoasterLogLevel level, threshold;
		const char* file;
		const char* strLevel;
		char ts[TS_LEN + 1];
		bool startOfItem;
		Lock lock;
		
		/* Disable default copy constructor */
		Logger(const Logger&);
		/* Disable default assignment */
		Logger& operator=(const Logger&);
	protected:
		Logger(std::ostream& out);
		void setLevel(CoasterLogLevel level);
		const char* levelToStr(CoasterLogLevel level);
		void header();
		char* timeStamp();
		void commitBuffer();
	public:
		virtual ~Logger();
		Logger& operator<< (CoasterLogLevel level);
		Logger& operator<< (const std::string& str);
		Logger& operator<< (const std::string* str);
		Logger& operator<< (const char* str);
		Logger& operator<< (int i);
		Logger& operator<< (long l);
		Logger& operator<< (Logger& ( *pf )(Logger&));
		Logger& setFile(const char* file);
		void endItem();
		void log(CoasterLogLevel level, const char* fileName, const char* msg);
		void log(CoasterLogLevel level, const char* fileName, std::string msg);

		void setThreshold(CoasterLogLevel level);

		static Logger& singleton();
};

class StdoutLogger: public Logger {
	public:
		StdoutLogger();
		virtual ~StdoutLogger();
};

Logger& endl(Logger& l);

#define LogError Logger::singleton().setFile(__FILE__) << Logger::ERROR
#define LogWarn Logger::singleton().setFile(__FILE__) << Logger::WARN
#define LogInfo Logger::singleton().setFile(__FILE__) << Logger::INFO
#define LogDebug Logger::singleton().setFile(__FILE__) << Logger::DEBUG

}

#endif /* LOGGER_H_ */
