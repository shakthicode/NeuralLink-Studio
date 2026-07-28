#include <cmath>
#include <iomanip>
#include <iostream>
#include <regex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

double number_field(const std::string& json, const std::string& name, double fallback = 0.0) {
    const std::regex pattern(
        "\"" + name + "\"\\s*:\\s*(-?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");
    std::smatch match;
    return std::regex_search(json, match, pattern) ? std::stod(match[1].str()) : fallback;
}

long long integer_field(const std::string& json, const std::string& name, long long fallback = 0) {
    return static_cast<long long>(number_field(json, name, static_cast<double>(fallback)));
}

std::string string_field(const std::string& json, const std::string& name) {
    const std::regex pattern("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"");
    std::smatch match;
    return std::regex_search(json, match, pattern) ? match[1].str() : std::string();
}

bool boolean_field(const std::string& json, const std::string& name, bool fallback = false) {
    const std::regex pattern("\"" + name + "\"\\s*:\\s*(true|false)");
    std::smatch match;
    return std::regex_search(json, match, pattern) ? match[1].str() == "true" : fallback;
}

std::vector<double> number_list_field(const std::string& json, const std::string& name) {
    std::vector<double> values;
    std::stringstream stream(string_field(json, name));
    std::string token;
    while (std::getline(stream, token, ',')) {
        if (!token.empty()) {
            values.push_back(std::stod(token));
        }
    }
    return values;
}

void success(long long id, double value) {
    std::cout << "{\"id\":" << id << ",\"ok\":true,\"value\":"
              << std::setprecision(17) << value << "}" << std::endl;
}

void error(long long id, const std::string& message) {
    std::cout << "{\"id\":" << id << ",\"ok\":false,\"error\":\""
              << message << "\"}" << std::endl;
}

}  // namespace

int main() {
    std::ios::sync_with_stdio(false);
    std::cin.tie(nullptr);

    std::string line;
    while (std::getline(std::cin, line)) {
        const long long id = integer_field(line, "id");
        try {
            const std::string command = string_field(line, "command");
            if (command == "ping") {
                success(id, 0.0);
                continue;
            }
            if (command != "compute") {
                error(id, "unsupported command");
                continue;
            }

            const std::string block = string_field(line, "block");
            const double input = number_field(line, "input");
            const double amplitude = number_field(line, "amplitude", 1.0);
            const double frequency = number_field(line, "frequency", 1.0);
            const double phase = number_field(line, "phase");
            const double bias = number_field(line, "bias");
            const double parameter = number_field(line, "parameter");
            const double dt = number_field(line, "dt");
            const double current_output = number_field(line, "currentOutput");
            const double previous_input = number_field(line, "previousInput", input);
            const bool has_previous_input = boolean_field(line, "hasPreviousInput");
            const std::string signs = string_field(line, "signs");
            const std::string solver = string_field(line, "solver");
            const std::vector<double> inputs = number_list_field(line, "inputs");

            if (block == "Clock" || block == "Scope") {
                success(id, input);
            } else if (block == "Sine") {
                success(id, amplitude * std::sin(frequency * input + phase) + bias);
            } else if (block == "Cosine") {
                success(id, amplitude * std::cos(frequency * input + phase) + bias);
            } else if (block == "Constant") {
                success(id, parameter);
            } else if (block == "Gain") {
                success(id, input * parameter);
            } else if (block == "Display") {
                success(id, input);
            } else if (block == "Sum") {
                double output = 0.0;
                bool first = true;
                for (std::size_t i = 0; i < signs.size(); ++i) {
                    const double value = i < inputs.size() ? inputs[i] : 0.0;
                    const char op = signs[i];
                    if (first) {
                        output = op == '-' ? -value
                                : (op == '/' ? (value != 0.0 ? 1.0 / value : 0.0) : value);
                        first = false;
                    } else if (op == '+') {
                        output += value;
                    } else if (op == '-') {
                        output -= value;
                    } else if (op == '*') {
                        output *= value;
                    } else if (op == '/') {
                        output = value != 0.0 ? output / value : 0.0;
                    }
                }
                success(id, output);
            } else if (block == "Integrator") {
                const double prior = has_previous_input ? previous_input : input;
                double increment;
                if (solver == "MIDPOINT_RK2") {
                    increment = 0.5 * (prior + input) * dt;
                } else if (solver == "RK4") {
                    const double k1 = prior;
                    const double k2 = prior + (input - prior) * 0.5;
                    const double k3 = k2;
                    const double k4 = input;
                    increment = (dt / 6.0) * (k1 + 2.0 * k2 + 2.0 * k3 + k4);
                } else {
                    increment = input * dt;
                }
                success(id, current_output + increment);
            } else {
                error(id, "unsupported block");
            }
        } catch (const std::exception& ex) {
            error(id, ex.what());
        }
    }
    return 0;
}
