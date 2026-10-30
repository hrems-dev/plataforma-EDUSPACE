import 'package:flutter/material.dart';

const colorMarca = Color(0xFF1F5FBF);

ThemeData temaEduspace() => ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: colorMarca),
      scaffoldBackgroundColor: const Color(0xFFF5F7FB),
      appBarTheme: const AppBarTheme(centerTitle: false, scrolledUnderElevation: 0),
      cardTheme: CardThemeData(
        elevation: 0,
        margin: EdgeInsets.zero,
        color: Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16), side: const BorderSide(color: Color(0xFFE3E8F0))),
      ),
      inputDecorationTheme: InputDecorationTheme(border: OutlineInputBorder(borderRadius: BorderRadius.circular(12))),
      filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(minimumSize: const Size(48, 48), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)))),
    );

/// Colores estables por curso (misma idea que las tarjetas de la web).
const _paleta = [Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFF059669), Color(0xFFD97706), Color(0xFFDB2777), Color(0xFF0891B2), Color(0xFF4F46E5), Color(0xFFDC2626)];
Color colorCurso(int id) => _paleta[id.abs() % _paleta.length];
