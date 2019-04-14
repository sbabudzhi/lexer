import java.util.NoSuchElementException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.Optional;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


/*
Грамматика. Стартовый символ -- S.
Токены: операторы + - * / ^; скобки ( ) ; Var для идентификаторов переменных
Val для значений, ключевое слово var, конец строки EOF.
S  -> H EOF
H  -> E | var Var = E
E  -> T E'
E' -> + T E' | - T E' | ε
T  -> F T'
T' -> * F T' | / F T' | ε
F  -> V F'
F' -> ^ F | ε
V  -> V | - V
V'  -> Var | Val | ( E )
*/

public class ParserV2 {
    /** Регулярные выражения*/
    // Пробельные символы обычно выносят отдельно, см. так же ниже
    private static Pattern whitespace = Pattern.compile("^\\s+");
    private static Pattern var = Pattern.compile("^[a-zA-Z]+");
    private static Pattern kw_var = Pattern.compile("^var");
    // Для значений всё же регулярное выражение по идее выглядит как-то так:
    private static Pattern val = Pattern.compile("^\\d+(\\.\\d*)?([eE][+-]?\\d+)?");
    private static Pattern op = Pattern.compile("^[+*/^=\\-]");
    private static Pattern parenthesis = Pattern.compile("^[()]");
    // Разбираемая строка
    private static String expr;
    /** Таблица символов */
    private static HashMap<String,Double> symtable = new HashMap<>();
    // Текущий токен. В данном случае достаточно одного. В общем случае
    // может быть больше.
    private static Token currentToken;
    // Текущее положение в разбираемой строке
    private static int pos = 0;

    public static void main(String[] args) {
        Scanner s = new Scanner(System.in);
        while(true) {
          try {
            double result = parseS(s.nextLine());
            System.out.println("Выражение = " + result);
          } catch (NoSuchElementException e) {
            // Срабатывает если входные строки закончились
            // программа завершается.
            break;
          } catch (Exception e) {
            System.err.println(e.getMessage());
          }
        }
        s.close();
    }

    /**Получить следующий токен*/
    private static Token getNextToken() throws Exception {
        // Если входная строка пустая, сигнализируем об этом.
        // Если не вводить токен конца строки, есть шанс, что
        // парсер разберёт не всю входную строку.
        // Правда, в данном случае это будет означать, что в конце
        // строки мусор. Но не всегда.
        if(expr.substring(pos).isEmpty()) {
          return new Token(Type.EOF, "");
        }

        String ss = expr.substring(pos);
        Matcher m = whitespace.matcher(ss);

        // Пропустить пробельные символы в начале подстроки
        if (m.find()) {
          pos += m.group().length();
        }

        // Новая подстрока создаётся поскольку используемые ниже
        // лямбда-выражения требуют чтобы захватываемые локальные
        // переменные были "effectively final".
        String ss2 = expr.substring(pos);
        // Сравнить подстроку с шаблонами лексем:
        // Возможно, не самый красивый код, но в двух словах.
        // Stream используется чтобы построить цепочку из Optional,
        // в свою очередь Supplier -- чтобы они вычислялись "лениво"
        Token res =
          Stream.<Supplier<Optional<Token>>>of(
          // Здесь важен порядок: шаблон kw_var пересекается с шаблоном
          // var, поэтому важно чтобы kw_var был проверен раньше (
          // поскольку он описывает строгое подмножество строк совпадающих
          // с var)
          () -> matchToken(ss2, kw_var, Type.KW_VAR),
          () -> matchToken(ss2, var, Type.VAR),
          () -> matchToken(ss2, val, Type.VAL),
          () -> matchToken(ss2, op,  Type.OP ),
          () -> matchToken(ss2, parenthesis, Type.PARENT)
          )
          .map(Supplier::get)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .findFirst()
          .orElseThrow(() -> new Exception("Неизвестный токен: " + expr.substring(pos)))
          ;
        return res;
    }

    /** Функция сравнения строки с шаблоном токена.
    Если находит совпадение, перемещает указатель pos на длину токена
    и возвращает токен.
    */
    private static Optional<Token> matchToken(String s, Pattern pat, Type type) {
      Matcher m = pat.matcher(s);
      if (m.find()) {
        pos += m.group().length();
        return Optional.of(new Token(type, m.group()));
      }
      else {
        return Optional.empty();
      }
    }

    /** Стартовое правило S.
    Помимо собственно тривиального разбора стартового правила,
    делает ещё несколько вещей:
    1. Сбрасывает положение в разбираемой строке.
    2. Сохраняет входную строку в поле класса.
    3. Читает первый токен.

    Эти действия делаются здесь потому что стартовое правило
    вызывается ровно 1 раз на строку.
    */
    private static double parseS(String in_expr) throws Exception {
      pos = 0;
      expr = in_expr;
      currentToken = getNextToken();

      // S  -> H EOF
      double res = parseH();
      // Проверка что последний токен -- EOF, т.е. строка действительно закончилась
      if (!currentToken.t.equals(Type.EOF)) throw new Exception("Expected EOF, got " + currentToken.c);
      return res;
    }

    // Правило H.
    // H может иметь вид либо просто выражения,
    // либо присваивания.
    // H  -> E | var Var = E
    private static double parseH() throws Exception {
      if (currentToken.t.equals(Type.KW_VAR)) {
        // Присваивание начинается на ключевое слово var.
        currentToken = getNextToken();
        // Далее должно идти название переменной
        if (!currentToken.t.equals(Type.VAR)) throw new Exception("Expected VAR, got " + currentToken.t.toString());
        String varName = currentToken.c;
        currentToken = getNextToken();
        // знак равенства
        if (!currentToken.t.equals(Type.OP) || !currentToken.c.equals("=")) throw new Exception("Expected =, got " + currentToken.c);
        currentToken = getNextToken();
        // и выражение, которому равна переменная
        double res = parseE();
        symtable.put(varName, res);
        return res;
      } else {
        // В противном случае это просто выражение.
        return parseE();
      }
    }

    // Правило E. Разбирает верхний уровень выражения.
    // E  -> T E'
    private static double parseE() throws Exception {
      // Здесь и далее, следует помнить, что сначала
      // вычисляется аргумент функции/метода, и только
      // потом происходит вызов самого метода.
      // Следовательно, если в грамматике  мы видим
      // E -> T E'
      // то здесь сначала вызывается parseT(),
      // затем его результат передаётся в parseEprime(...)
      // i.e. разбор, как и положено, происходит слева направо
      return parseEprime(parseT());
    }

    // Правило T. Разбирает подвыражения, являющиеся операндами
    // сложения и вычитания
    // T  -> F T'
    private static double parseT() throws Exception {
      return parseTprime(parseF());
    }

    // Правило F. Разбирает подвыражения, являющиеся операндами
    // умножения и деления
    // F  -> V F'
    private static double parseF() throws Exception {
      return parseFprime(parseV());
    }

    // Правило V. Разбирает унарный минус.
    // V  -> V | - V
    private static double parseV() throws Exception {
      if (currentToken.c.equals("-")) {
        currentToken = getNextToken();
        return - parseVprime();
      } else {
        return parseVprime();
      }
    }

    // Правило V'. Разбирает конечное значение или выражение в скобках.
    // V'  -> Var | Val | ( E )
    private static double parseVprime() throws Exception {
      if (currentToken.c.equals("(")) {
        // Выражение в скобках
        currentToken = getNextToken();
        double res = parseE();
        // Должно заканчиваться закрывающей скобкой!
        if (!currentToken.c.equals(")")) throw new Exception("Expected ), got " + currentToken.c);
        currentToken = getNextToken();
        return res;
      } else if (currentToken.t.equals(Type.VAR)) {
        // Переменная
        double res = symtable.get(currentToken.c);
        currentToken = getNextToken();
        return res;
      } else if (currentToken.t.equals(Type.VAL)) {
        // Значение
        double res = Double.parseDouble(currentToken.c);
        currentToken = getNextToken();
        return res;
      } else {
        // Неожиданный токен!
        throw new Exception("Expected VAR, VAL or (, but got " + currentToken.c);
      }
    }

    // Правило T'. Разбирает правую часть для бинарных операторов * и /
    // агрументом получает значение уже разобранной левой части.
    // Если текущий токен не * и не /, значит всё выражение состоит
    // только из первого операнда.
    // T' -> * F T' | / F T' | ε
    private static double parseTprime(double lhs) throws Exception {
      if (currentToken.t.equals(Type.OP) && currentToken.c.equals("*")) {
        // Умножение
        currentToken = getNextToken();
        return parseTprime(lhs * parseF());
      } else if (currentToken.t.equals(Type.OP) && currentToken.c.equals("/")) {
        // Деление
        currentToken = getNextToken();
        return parseTprime(lhs / parseF());
      } else {
        // Только левая часть
        return lhs;
      }
    }

    // Правило E'. Разбирает правую часть для бинарных операторов + и -
    // агрументом получает значение уже разобранной левой части.
    // Если текущий токен не + и не -, значит всё выражение состоит
    // только из первого операнда.
    // E' -> + T E' | - T E' | ε
    private static double parseEprime(double lhs) throws Exception {
      if (currentToken.t.equals(Type.OP) && currentToken.c.equals("+")) {
        // Сложение
        currentToken = getNextToken();
        return parseEprime(lhs + parseT());
      } else if (currentToken.t.equals(Type.OP) && currentToken.c.equals("-")) {
        // Вычитание
        currentToken = getNextToken();
        return parseEprime(lhs - parseT());
      } else {
        // Только левая часть
        return lhs;
      }
    }

    // Правило F'. Разбирает правую часть для бинарного оператора ^
    // агрументом получает значение уже разобранной левой части.
    // Если текущий токен не ^, значит всё выражение состоит
    // только из первого операнда.
    // F' -> ^ F | ε
    private static double parseFprime(double lhs) throws Exception {
      if (currentToken.t.equals(Type.OP) && currentToken.c.equals("^")) {
        // Возведение в степень
        // Следует заметить, что в качестве правого операнда здесь
        // выступает не V F', как было бы по аналогии с правилами
        // E' и T', а F. Использование F вместо V F' делает оператор
        // ^ правоассоциативным, поскольку при разборе выражения
        // 1^2^3, 2^3 должно быть разобрано и вычислено (по правилу F)
        // до того, как будет разобрано и вычислено всё выражение целиком.
        currentToken = getNextToken();
        return Math.pow(lhs, parseF());
      } else {
        return lhs;
      }
    }

    /** Тип токена
     * Переменная
     * Значение
     * Оператор
     * Скобки
     * Ключевое слово var
     * Конец строки
     */
    public enum Type {
        VAR, VAL, OP, PARENT, KW_VAR, EOF
    }
    public static class Token {
        public Type t;
        public String c;

        public Token(Type t, String c) {
            this.t = t;
            this.c = c;
        }
    }
}
