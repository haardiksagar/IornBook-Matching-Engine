package com.ironbook.matching_engine.Log;

import com.ironbook.matching_engine.Book.OrderBook;
import com.ironbook.matching_engine.Model.Order;
import com.ironbook.matching_engine.Model.Side;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Reads a WriteAheadLog file from the top and replays every order
 * through the OrderBook, in the exact order they were originally
 * written.
 *
 * Important: this does NOT need to know which orders were already
 * matched before the crash. submitOrder() is deterministic - replaying
 * the same sequence of orders from an empty book reproduces the exact
 * same matches, automatically. There is no "already done" tracking
 * anywhere in this class, on purpose.
 */
public class LogReplayer {


}