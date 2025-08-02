#!/usr/bin/env node

const { Pool } = require('pg');
const express = require('express');
require('dotenv').config();

// Database connection
const pool = new Pool({
  user: process.env
