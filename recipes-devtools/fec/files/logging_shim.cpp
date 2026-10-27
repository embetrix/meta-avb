// Replaces AOSP libbase logging.cpp, which GCC cannot build: it calls
// __builtin_available(android 30, *) from nine sites that sit outside its
// __ANDROID__ guard, and that builtin is Clang-only. Everything else in the
// dependency set compiles with GCC, so this is the whole cost of not requiring
// Clang for one build-time tool.
//
// Only what libsparse and libfec actually reference via LOG()/CHECK() is
// implemented. No FEC logic lives here.

#include <android-base/logging.h>

#include <cstdlib>
#include <cstring>
#include <iostream>
#include <sstream>
#include <string>

namespace android {
namespace base {

struct LogMessageData {
    std::ostringstream stream;
    const char* file = nullptr;
    unsigned int line = 0;
    LogSeverity severity = INFO;
    const char* tag = nullptr;
    int error = -1;
};

LogMessage::LogMessage(const char* file, unsigned int line, LogId, LogSeverity severity,
                       const char* tag, int error)
    : LogMessage(file, line, severity, tag, error) {}

LogMessage::LogMessage(const char* file, unsigned int line, LogSeverity severity, const char* tag,
                       int error)
    : data_(new LogMessageData) {
    data_->file = file;
    data_->line = line;
    data_->severity = severity;
    data_->tag = tag;
    data_->error = error;
}

LogMessage::~LogMessage() {
    std::string msg = data_->stream.str();
    if (data_->error != -1) {
        msg += ": ";
        msg += strerror(data_->error);
    }
    LogLine(data_->file, data_->line, data_->severity, data_->tag, msg.c_str());
    if (data_->severity == FATAL) {
        abort();
    }
}

std::ostream& LogMessage::stream() {
    return data_->stream;
}

void LogMessage::LogLine(const char* file, unsigned int line, LogSeverity severity, const char* tag,
                         const char* msg) {
    static const char* const kLevel[] = {"V", "D", "I", "W", "E", "F", "F"};
    std::cerr << (tag ? tag : "fec") << ' ' << kLevel[severity] << ' ' << (file ? file : "?") << ':'
              << line << ": " << (msg ? msg : "") << '\n';
}

// FATAL must stay loggable. On host MUST_LOG_MESSAGE is false, so WOULD_LOG
// consults this alone; returning false for FATAL would make a failing CHECK()
// skip constructing the LogMessage whose destructor does the abort, turning
// every CHECK into a no-op.
bool ShouldLog(LogSeverity severity, const char*) {
    return severity >= WARNING;
}

}  // namespace base
}  // namespace android
