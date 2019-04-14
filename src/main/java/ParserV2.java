import java.util.NoSuchElementException;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.Optional;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ParserV2 {
    /** Регулярные выражения*/
    private static Pattern whitespace = Pattern.compile("^\\s+");
    private static Pattern var = Pattern.compile("^[a-zA-Z]+");
    private static Pattern kw_var = Pattern.compile("^var");
    private static Pattern val = Pattern.compile("^\\d+(\\.\\d*)?([eE][+-]?\\d+)?");
    private static Pattern op = Pattern.compile("^[+*/^=\\-]");
    private static Pattern parenthesis = Pattern.compile("^[()]");
    private static String expr;
    /** Таблица символов */
    private static HashMap<String,Double> symtable = new HashMap<>();
    private static Token currentToken;
    private static int pos = 0;

    public static void main(String[] args) {
        Scanner s = new Scanner(System.in);
        while(true) {
          try {
            double result = parseS(s.nextLine());
            System.out.println("Выражение = " + result);
          } catch (NoSuchElementException e) {
            break;
          } catch (Exception e) {
            System.err.println(e.getMessage());
          }
        }
        s.close();
    }

    /**Получить следующий токен*/
    private static Token getNextToken() throws Exception {
        if(expr.substring(pos).isEmpty()) {
          return new Token(Type.EOF, "");
        }

        String ss = expr.substring(pos);
        Matcher m = whitespace.matcher(ss);

        // Пропустить пробельные символы в начале подстроки
        if (m.find()) {
          pos += m.group().length();
        }

        String ss2 = expr.substring(pos);
        // Сравнить подстроку с шаблонами лексем:
        Token res =
          Stream.<Supplier<Optional<Token>>>of(
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

    /** Разбирает токены и выполняет действия с ними */

    private static double parseS(String in_expr) throws Exception {
      pos = 0;
      expr = in_expr;
      currentToken = getNextToken();

      double res = parseH();
      if (!currentToken.t.equals(Type.EOF)) throw new Exception("Expected EOF, got " + currentToken.c);
      return res;
    }

    private static double parseH() throws Exception {
      if (currentToken.t.equals(Type.KW_VAR)) {
        currentToken = getNextToken();
        if (!currentToken.t.equals(Type.VAR)) throw new Exception("Expected VAR, got " + currentToken.t.toString());
        String varName = currentToken.c;
        currentToken = getNextToken();
        if (!currentToken.t.equals(Type.OP) || !currentToken.c.equals("=")) throw new Exception("Expected =, got " + currentToken.c);
        currentToken = getNextToken();
        double res = parseE();
        symtable.put(varName, res);
        return res;
      } else {
        return parseE();
      }
    }

    private static double parseE() throws Exception {
      return parseEprime(parseT());
    }

    private static double parseT() throws Exception {
      return parseTprime(parseF());
    }

    private static double parseF() throws Exception {
      return parseFprime(parseV());
    }

    private static double parseV() throws Exception {
      if (currentToken.c.equals("-")) {
        currentToken = getNextToken();
        return - parseVprime();
      } else {
        return parseVprime();
      }
    }

    private static double parseVprime() throws Exception {
      if (currentToken.c.equals("(")) {
        currentToken = getNextToken();
        double res = parseE();
        if (!currentToken.c.equals(")")) throw new Exception("Expected ), got " + currentToken.c);
        currentToken = getNextToken();
        return res;
      } else if (currentToken.t.equals(Type.VAR)) {
        double res = symtable.get(currentToken.c);
        currentToken = getNextToken();
        return res;
      } else if (currentToken.t.equals(Type.VAL)) {
        double res = Double.parseDouble(currentToken.c);
        currentToken = getNextToken();
        return res;
      } else {
        throw new Exception("Expected VAR, VAL or (, but got " + currentToken.c);
      }
    }

    private static double parseTprime(double lhs) throws Exception {
      if (currentToken.t.equals(Type.OP) && currentToken.c.equals("*")) {
        currentToken = getNextToken();
        return parseTprime(lhs * parseF());
      } else if (currentToken.t.equals(Type.OP) && currentToken.c.equals("/")) {
        currentToken = getNextToken();
        return parseTprime(lhs / parseF());
      } else {
        return lhs;
      }
    }

    private static double parseEprime(double lhs) throws Exception {
      if (currentToken.t.equals(Type.OP) && currentToken.c.equals("+")) {
        currentToken = getNextToken();
        return parseEprime(lhs + parseT());
      } else if (currentToken.t.equals(Type.OP) && currentToken.c.equals("-")) {
        currentToken = getNextToken();
        return parseEprime(lhs - parseT());
      } else {
        return lhs;
      }
    }

    private static double parseFprime(double lhs) throws Exception {
      if (currentToken.t.equals(Type.OP) && currentToken.c.equals("^")) {
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
