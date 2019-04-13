import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

public class ParserV2 {
    /** Регулярные выражения*/
    private static Pattern var = Pattern.compile("^[ ]*[a-zA-Z]+");
    private static Pattern valv2 =  Pattern.compile("^[ ]*((-?\\d+\\.?\\d+)|(\\.)|\\d+)");
    private static Pattern op = Pattern.compile("^[ ]*([+*/^=\\-])");
    private static Pattern parenthesis = Pattern.compile("^[ ]*\\(|\\)");
    /** Абстрактный результат. В поле result.val будут записываться промежуточные вычисления*/
    private static Result result = new Result();
    /** Таблица символов */
    private static HashMap<String,Double> symtable = new HashMap<>();

    public static void main(String[] args) throws Exception {
        StringBuilder expression = new StringBuilder(new Scanner(System.in).nextLine());
        List<Token> tokens = new ArrayList<>();
        while(!expression.toString().isEmpty()) {
            tokens.add(getNextToken(expression));
        }
        while(tokens.size() > 1)
            lexer(tokens);
        if(result.getVar()!=null) {
            System.out.println("Переменная "+ result.getVar() + " = " + result.getVal());
        }else{
            System.out.println("Выражение = " + result.getVal());
        }

    }

    /**Получить следующий токен*/
    private static Token getNextToken(StringBuilder expr) throws Exception {
        int pos = 0;
        while(!expr.substring(pos).isEmpty()) {
            while (expr.charAt(pos) == ' ')
                pos++;
            if (pos < expr.length() && var.matcher(expr).find()) { return  getTokenForType(Type.VAR,var,pos,expr);
            } else if (pos < expr.length() && valv2.matcher(expr).find()) { return getTokenForType(Type.VAL,valv2,pos,expr);
            } else if (pos < expr.length() && op.matcher(expr).find()) { return getTokenForType(Type.OP,op,pos,expr);
            } else if (pos < expr.length() && parenthesis.matcher(expr).find()){ return getTokenForType(Type.PARENT, parenthesis,pos,expr);
            }
        }
        throw new Exception("Символ не распознан / строка пустая");
    }

    /** Искать конец лексемы в строке. Вернуть найденный токен*/
    private static Token getTokenForType(Type type, Pattern pattern, int pos, StringBuilder str){
        String token ="";
        while(pos < str.length() && pattern.matcher(String.valueOf(str.charAt(pos))).find()){
            token = token + str.charAt(pos);
            pos++;
        }
        str.delete(0,pos);
        return new Token(type, token.trim());
    }
    /** Разбор токенов (выделено в отдельный метод, чтобы можно было вызывать рекурсивно)*/
    private static void lexer(List<Token> tokens){
        for(int i = 0; i < tokens.size(); i++){
            i = caseImitator(tokens,i);
        }
    }

    /** Удалить токены, которые уже вычислены*/
    private static int clearTokens(List<Token> tokens, int i, Result result){
        tokens.get(i-1).c = result.getVal().toString();
        tokens.remove(i+1);
        tokens.remove(i);
        return i - 2;
    }
    /** Удалить скобки после вычисления результата в них*/
    private static int clearTokensForParent(List<Token> tokens, int i, Result result){
        tokens.get(i).c = result.getVal().toString();
        tokens.remove(i-2);
        tokens.remove(i-1);
        return 0;
    }
    /** Разбирает токены и выполняет действия с ними */
    private static int caseImitator(List<Token> tokens, int i){
        Token token  = tokens.get(i);
        switch (token.t) {
            case OP:
                if (token.c.equals("+")){
                    if(i + 2  == tokens.size() ||(i + 2 < tokens.size() && !isMulDiv(tokens.get(i + 2)) && !isExp(tokens.get(i + 2)))){
                        result.setVal(doAdd(Double.parseDouble(tokens.get(i-1).c),Double.parseDouble(tokens.get(i+1).c)));
                        i =clearTokens(tokens,i,result);
                    }else{
                        i = getValueForHighPriority(tokens,i);
                    }
                } else if (token.c.equals("-")){
                    if(i + 2 < tokens.size() && !isMulDiv(tokens.get(i + 2)) && !isExp(tokens.get(i + 2))) {
                        result.setVal(doSub(Double.parseDouble(tokens.get(i - 1).c), Double.parseDouble(tokens.get(i + 1).c)));
                        i =clearTokens(tokens,i,result);
                    }else{
                        i = getValueForHighPriority(tokens,i);
                    }
                } else if (token.c.equals("*")){
                    result.setVal(doMul(Double.parseDouble(tokens.get(i-1).c),Double.parseDouble(tokens.get(i+1).c)));
                    i =clearTokens(tokens,i,result);
                } else if (token.c.equals("/")){
                    result.setVal(doDiv(Double.parseDouble(tokens.get(i-1).c),Double.parseDouble(tokens.get(i+1).c)));
                    i =clearTokens(tokens,i,result);
                } else if (token.c.equals("^")){
                    result.setVal(doExp(Double.parseDouble(tokens.get(i-1).c),Double.parseDouble(tokens.get(i+1).c)));
                    i =clearTokens(tokens,i,result);
                }
                break;
            case VAL:
                if (result.getVal() == null) {
                    result.setVal(Double.parseDouble(token.c));
                }
                break;
            case VAR:
                if((i+2 < tokens.size() && tokens.get(i+1).c.equals("=") && valv2.matcher(tokens.get(i+2).c).find()) // это является просто присваиванием х = 1
                        || (i+3 < tokens.size() && !op.matcher(tokens.get(i+3).c).find() // после 1 нет никаких операторов
                            && !parenthesis.matcher(tokens.get(i+2).c).find()) ) { // после 1 нет никаких скобок
                    symtable.put(token.c, Double.parseDouble(tokens.get(i + 2).c));
                    tokens.remove(i);
                    tokens.remove(i);
                    result.setVar(token.c);
                }
                break;
            case PARENT:
                if(token.c.equals("("))
                    i =caseImitator(tokens,++i);
                else if(token.c.equals(")")){
                    i =clearTokensForParent(tokens,i,result);
                }
                break;
        }
        return i;
    }

    /** Посчитать значение всех возможных операций высшего приоритета до нормального числа, записать его в токен, вернуть позиция оператора - 1
     * pos - номер оператора (+,-)*/
    private static int getValueForHighPriority(List<Token> tokens, int pos){
        int i = pos;
        while(pos+2 < tokens.size() && isExp(tokens.get(pos +=2 ))){
            tokens.get(pos-1).c = doExp(Double.parseDouble(tokens.get(pos-1).c), Double.parseDouble(tokens.get(pos+1).c)).toString();
            tokens.remove(pos+1);
            tokens.remove(pos);
        }
        pos = i;
        while(pos+2 < tokens.size() && isMulDiv(tokens.get(pos+=2))){
            tokens.get(pos-1).c = doForOp(tokens.get(pos),Double.parseDouble(tokens.get(pos-1).c),Double.parseDouble(tokens.get(pos+1).c)).toString();
            tokens.remove(pos+1);
            tokens.remove(pos);
        }
        return i-1;

    }

    private static Double doAdd(Double op1, Double op2){
        return op1 + op2;
    }
    private static Double doSub(Double op1, Double op2){
        return op1 - op2;
    }
    private static Double doMul(Double op1, Double op2){
        return op1 * op2;
    }
    private static Double doDiv(Double op1, Double op2){
        return op1 / op2;
    }
    private static Double doExp(Double op1, Double op2){
        return Math.pow(op1, op2);
    }
    /** Если оператор * или / */
    private static boolean isMulDiv(Token token){
        if(token.c.equals("*") || token.c.equals("/")){
            return true;
        }
        return false;
    }
    /** Если оператор  ^ */
    private static boolean isExp(Token token){
        if(token.c.equals("^")){
            return true;
        }
        return false;
    }
    /** Сделать действие в зависимости от оператора*/
    private static Double doForOp (Token token, Double op1, Double op2){
        if(token.c.equals("*")){
            return doMul(op1,op2);
        } else if(token.c.equals("/")){
            return doDiv(op1,op2);
        }else if(token.c.equals("^")){
            return doExp(op1,op2);
        }
        return null;
    }

    /** Тип токена
     * Переменная
     * Значение
     * Оператор
     * Скобки
     */
    public enum Type {
        VAR,VAL,OP,PARENT
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
