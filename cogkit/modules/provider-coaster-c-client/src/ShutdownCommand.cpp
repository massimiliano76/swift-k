/*
 * Swift Parallel Scripting Language (http://swift-lang.org)
 *
 * Copyright 2014 University of Chicago
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/*
 * ShutdownCommand.cpp
 *
 *  Created on: Aug 28, 2014
 *      Author: Tim Armstrong
 */

#include "ShutdownCommand.h"
#include "Logger.h"
#include <sstream>

using namespace Coaster;

using std::string;

string ShutdownCommand::NAME("SHUTDOWN");

ShutdownCommand::ShutdownCommand(): Command(&NAME) {
}

ShutdownCommand::~ShutdownCommand() {
}

void ShutdownCommand::send(CoasterChannel* channel, CommandCallback* cb) {
	Command::send(channel, cb);
}

void ShutdownCommand::dataSent(Buffer* buf) {
	delete buf;
}

void ShutdownCommand::replyReceived() {
	Command::replyReceived();
}
