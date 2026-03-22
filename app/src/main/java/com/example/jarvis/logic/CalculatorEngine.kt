package com.example.jarvis.logic

import android.util.Log
import java.util.Stack
import kotlin.math.pow
import kotlin.math.sqrt

object CalculatorEngine {
    private const val TAG = "CalculatorEngine"

    fun evaluate(expression: String): String {
        return try {
            val rpn = shuntingYard(expression)
            val result = evaluateRPN(rpn)
            formatResult(result)
        } catch (e: Exception) {
            Log.e(TAG, "Calculation failed: ${e.message}")
            "Error"
        }
    }

    private fun formatResult(value: Double): String {
        return if (value % 1 == 0.0) {
            value.toLong().toString()
        } else {
            String.format("%.2f", value)
        }
    }

    private fun shuntingYard(expression: String): List<String> {
        val outputQueue = mutableListOf<String>()
        val operatorStack = Stack<String>()
        
        // Tokenize: numbers, operators, functions, parentheses
        // Regex splits by operators but keeps them, and handles decimal numbers
        val tokens = expression.replace(" ", "")
            .split(Regex("(?<=[-+*/%^()])|(?=[-+*/%^()])|(?<=sqrt)|(?=sqrt)"))
            .filter { it.isNotEmpty() }

        for (token in tokens) {
            when {
                token.matches(Regex("-?\\d+(\\.\\d+)?")) -> outputQueue.add(token) // Number
                token == "sqrt" -> operatorStack.push(token)
                token == "(" -> operatorStack.push(token)
                token == ")" -> {
                    while (operatorStack.isNotEmpty() && operatorStack.peek() != "(") {
                        outputQueue.add(operatorStack.pop())
                    }
                    if (operatorStack.isNotEmpty() && operatorStack.peek() == "(") {
                        operatorStack.pop() // Pop '('
                    }
                    if (operatorStack.isNotEmpty() && operatorStack.peek() == "sqrt") {
                        outputQueue.add(operatorStack.pop()) // Pop function
                    }
                }
                isOperator(token) -> {
                    while (operatorStack.isNotEmpty() && 
                           isOperator(operatorStack.peek()) && 
                           precedence(operatorStack.peek()) >= precedence(token)) {
                        outputQueue.add(operatorStack.pop())
                    }
                    operatorStack.push(token)
                }
            }
        }
        
        while (operatorStack.isNotEmpty()) {
            outputQueue.add(operatorStack.pop())
        }
        
        return outputQueue
    }

    private fun evaluateRPN(tokens: List<String>): Double {
        val stack = Stack<Double>()
        
        for (token in tokens) {
            when {
                token.matches(Regex("-?\\d+(\\.\\d+)?")) -> stack.push(token.toDouble())
                token == "sqrt" -> {
                    val a = stack.pop()
                    stack.push(sqrt(a))
                }
                isOperator(token) -> {
                    val b = stack.pop()
                    val a = stack.pop()
                    val result = when (token) {
                        "+" -> a + b
                        "-" -> a - b
                        "*" -> a * b
                        "/" -> if (b != 0.0) a / b else throw ArithmeticException("Division by zero")
                        "%" -> a * (b / 100.0) // "X percent of Y" -> Y * (X/100) logic usually implies X% * Y
                        "^" -> a.pow(b)
                        else -> 0.0
                    }
                    stack.push(result)
                }
            }
        }
        
        return stack.pop()
    }

    private fun isOperator(token: String): Boolean {
        return token == "+" || token == "-" || token == "*" || token == "/" || token == "%" || token == "^"
    }

    private fun precedence(op: String): Int {
        return when (op) {
            "+", "-" -> 1
            "*", "/" -> 2
            "%", "^" -> 3
            else -> 0
        }
    }
}
